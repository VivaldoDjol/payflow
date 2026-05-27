package com.gozzerks.payflow.event;

import com.gozzerks.payflow.config.RabbitMQConfig;
import com.gozzerks.payflow.exception.OrderNotFoundException;
import com.gozzerks.payflow.exception.PaymentGatewayException;
import com.gozzerks.payflow.service.PaymentService;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentConsumer — Edge Cases")
class PaymentConsumerEdgeCaseTest {

    @Mock
    private PaymentService paymentService;

    @InjectMocks
    private PaymentConsumer paymentConsumer;

    private static final PaymentRequestedEvent SAMPLE_EVENT =
            new PaymentRequestedEvent(1L, new BigDecimal("29.99"), "GBP", "test-key");

    private Message messageWithXDeath(List<Map<String, ?>> xDeathEntries) {
        Message message = MessageBuilder.withBody(new byte[0]).build();
        message.getMessageProperties().getHeaders().put("x-death", xDeathEntries);
        return message;
    }

    @Nested
    @DisplayName("Retry count boundary — MAX_RETRIES = 3")
    class RetryCountBoundaries {

        @Test
        @DisplayName("Should process payment when retry count is exactly MAX_RETRIES - 1 (2)")
        void shouldProcessAtRetryCountJustBelowMax() {
            // Arrange
            Message message = messageWithXDeath(List.of(
                    Map.of("queue", RabbitMQConfig.PAYMENT_QUEUE,
                            "reason", "rejected",
                            "count", 2L)
            ));
            doNothing().when(paymentService).processPayment(1L);

            // Act & Assert
            assertThatCode(() -> paymentConsumer.handlePaymentRequest(SAMPLE_EVENT, message))
                    .doesNotThrowAnyException();

            verify(paymentService).processPayment(1L);
            verify(paymentService, never()).markAsFailed(anyLong());
        }

        @Test
        @DisplayName("Should mark as failed when retry count is exactly MAX_RETRIES (3)")
        void shouldMarkAsFailedAtExactlyMaxRetries() {
            // Arrange
            Message message = messageWithXDeath(List.of(
                    Map.of("queue", RabbitMQConfig.PAYMENT_QUEUE,
                            "reason", "rejected",
                            "count", (long) RabbitMQConfig.MAX_RETRIES)
            ));

            // Act
            paymentConsumer.handlePaymentRequest(SAMPLE_EVENT, message);

            // Assert
            verify(paymentService).markAsFailed(1L);
            verify(paymentService, never()).processPayment(anyLong());
        }

        @Test
        @DisplayName("Should mark as failed when retry count exceeds MAX_RETRIES (4)")
        void shouldMarkAsFailedWhenExceedingMaxRetries() {
            // Arrange
            Message message = messageWithXDeath(List.of(
                    Map.of("queue", RabbitMQConfig.PAYMENT_QUEUE,
                            "reason", "rejected",
                            "count", 4L)
            ));

            // Act
            paymentConsumer.handlePaymentRequest(SAMPLE_EVENT, message);

            // Assert
            verify(paymentService).markAsFailed(1L);
            verify(paymentService, never()).processPayment(anyLong());
        }

        @Test
        @DisplayName("Should process when retry count is 0")
        void shouldProcessWhenRetryCountIsZero() {
            // Arrange
            Message message = messageWithXDeath(List.of(
                    Map.of("queue", RabbitMQConfig.PAYMENT_QUEUE,
                            "reason", "rejected",
                            "count", 0L)
            ));
            doNothing().when(paymentService).processPayment(1L);

            // Act
            paymentConsumer.handlePaymentRequest(SAMPLE_EVENT, message);

            // Assert
            verify(paymentService).processPayment(1L);
            verify(paymentService, never()).markAsFailed(anyLong());
        }
    }

    @Nested
    @DisplayName("x-death header parsing edge cases")
    class XDeathParsing {

        @Test
        @DisplayName("Should return 0 retries when x-death is null (first delivery)")
        void shouldReturnZeroWhenXDeathIsNull() {
            // Arrange
            Message message = MessageBuilder.withBody(new byte[0]).build();
            // No x-death header at all
            doNothing().when(paymentService).processPayment(1L);

            // Act
            paymentConsumer.handlePaymentRequest(SAMPLE_EVENT, message);

            // Assert
            verify(paymentService).processPayment(1L);
        }

        @Test
        @DisplayName("Should return 0 retries when x-death is empty list")
        void shouldReturnZeroWhenXDeathIsEmpty() {
            // Arrange
            Message message = messageWithXDeath(List.of());
            doNothing().when(paymentService).processPayment(1L);

            // Act
            paymentConsumer.handlePaymentRequest(SAMPLE_EVENT, message);

            // Assert
            verify(paymentService).processPayment(1L);
        }

        @Test
        @DisplayName("Should ignore x-death entries from different queues")
        void shouldIgnoreEntriesFromDifferentQueues() {
            // Arrange
            Message message = messageWithXDeath(List.of(
                    Map.of("queue", "some.other.queue",
                            "reason", "rejected",
                            "count", 10L)
            ));
            doNothing().when(paymentService).processPayment(1L);

            // Act
            paymentConsumer.handlePaymentRequest(SAMPLE_EVENT, message);

            // Assert - 10 retries on other queue are ignored → retry count = 0
            verify(paymentService).processPayment(1L);
            verify(paymentService, never()).markAsFailed(anyLong());
        }

        @Test
        @DisplayName("Should ignore x-death entries with non-rejected reason")
        void shouldIgnoreNonRejectedReason() {
            // Arrange
            Message message = messageWithXDeath(List.of(
                    Map.of("queue", RabbitMQConfig.PAYMENT_QUEUE,
                            "reason", "expired",
                            "count", 10L)
            ));
            doNothing().when(paymentService).processPayment(1L);

            // Act
            paymentConsumer.handlePaymentRequest(SAMPLE_EVENT, message);

            // Assert - "expired" reason is ignored → retry count = 0
            verify(paymentService).processPayment(1L);
        }

        @Test
        @DisplayName("Should sum retry counts from multiple matching x-death entries")
        void shouldSumMultipleMatchingEntries() {
            // Arrange - two entries individually below MAX_RETRIES but summing to >= MAX_RETRIES
            Message message = messageWithXDeath(List.of(
                    Map.of("queue", RabbitMQConfig.PAYMENT_QUEUE,
                            "reason", "rejected",
                            "count", 2L),
                    Map.of("queue", RabbitMQConfig.PAYMENT_QUEUE,
                            "reason", "rejected",
                            "count", 1L)
            ));

            // Act
            paymentConsumer.handlePaymentRequest(SAMPLE_EVENT, message);

            // Assert - sum = 3 = MAX_RETRIES → should mark as failed
            verify(paymentService).markAsFailed(1L);
            verify(paymentService, never()).processPayment(anyLong());
        }

        @Test
        @DisplayName("Should only count matching entries in mixed x-death list")
        void shouldOnlyCountMatchingInMixedList() {
            // Arrange
            Message message = messageWithXDeath(List.of(
                    Map.of("queue", RabbitMQConfig.PAYMENT_QUEUE,
                            "reason", "rejected",
                            "count", 1L),
                    Map.of("queue", "other.queue",
                            "reason", "rejected",
                            "count", 5L),
                    Map.of("queue", RabbitMQConfig.PAYMENT_QUEUE,
                            "reason", "expired",
                            "count", 5L)
            ));
            doNothing().when(paymentService).processPayment(1L);

            // Act
            paymentConsumer.handlePaymentRequest(SAMPLE_EVENT, message);

            // Assert - only 1 matching entry with count 1 → still below MAX_RETRIES
            verify(paymentService).processPayment(1L);
            verify(paymentService, never()).markAsFailed(anyLong());
        }
    }

    @Nested
    @DisplayName("Exception routing to DLQ")
    class ExceptionRouting {

        @Test
        @DisplayName("CircuitBreaker open should throw AmqpRejectAndDontRequeueException")
        void circuitBreakerOpenShouldReject() {
            // Arrange
            Message message = MessageBuilder.withBody(new byte[0]).build();
            doThrow(mock(CallNotPermittedException.class))
                    .when(paymentService).processPayment(1L);

            // Act & Assert
            assertThatThrownBy(() -> paymentConsumer.handlePaymentRequest(SAMPLE_EVENT, message))
                    .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        }

        @Test
        @DisplayName("RuntimeException should throw AmqpRejectAndDontRequeueException")
        void runtimeExceptionShouldReject() {
            // Arrange
            Message message = MessageBuilder.withBody(new byte[0]).build();
            doThrow(new RuntimeException("Unexpected"))
                    .when(paymentService).processPayment(1L);

            // Act & Assert
            assertThatThrownBy(() -> paymentConsumer.handlePaymentRequest(SAMPLE_EVENT, message))
                    .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        }

        @Test
        @DisplayName("PaymentGatewayException (extends RuntimeException) should route to DLQ")
        void paymentGatewayExceptionShouldRouteToDlq() {
            // Arrange
            Message message = MessageBuilder.withBody(new byte[0]).build();
            doThrow(new PaymentGatewayException("Gateway timeout"))
                    .when(paymentService).processPayment(1L);

            // Act & Assert
            assertThatThrownBy(() -> paymentConsumer.handlePaymentRequest(SAMPLE_EVENT, message))
                    .isInstanceOf(AmqpRejectAndDontRequeueException.class)
                    .hasCauseInstanceOf(PaymentGatewayException.class);
        }
    }

    @Nested
    @DisplayName("markAsFailed failure handling")
    class MarkAsFailedFailure {

        @Test
        @DisplayName("If markAsFailed throws, exception should propagate")
        void markAsFailedExceptionShouldPropagate() {
            // Arrange
            Message message = messageWithXDeath(List.of(
                    Map.of("queue", RabbitMQConfig.PAYMENT_QUEUE,
                            "reason", "rejected",
                            "count", (long) RabbitMQConfig.MAX_RETRIES)
            ));
            doThrow(new OrderNotFoundException("Order not found with ID: 1"))
                    .when(paymentService).markAsFailed(1L);

            // Act & Assert - this throws and the message will be requeued or lost
            assertThatThrownBy(() -> paymentConsumer.handlePaymentRequest(SAMPLE_EVENT, message))
                    .isInstanceOf(OrderNotFoundException.class)
                    .hasMessageContaining("Order not found");
        }
    }

    @Nested
    @DisplayName("Event data integrity")
    class EventDataIntegrity {

        @Test
        @DisplayName("Should use orderId from event, not from message body")
        void shouldUseOrderIdFromEvent() {
            // Arrange
            PaymentRequestedEvent event = new PaymentRequestedEvent(
                    42L, new BigDecimal("100.00"), "USD", "key-42");
            Message message = MessageBuilder.withBody(new byte[0]).build();
            doNothing().when(paymentService).processPayment(42L);

            // Act
            paymentConsumer.handlePaymentRequest(event, message);

            // Assert
            verify(paymentService).processPayment(42L);
        }

        @Test
        @DisplayName("Event with null orderId should propagate NullPointerException")
        void eventWithNullOrderId() {
            // Arrange
            PaymentRequestedEvent event = new PaymentRequestedEvent(
                    null, new BigDecimal("100.00"), "USD", "key-null");
            Message message = MessageBuilder.withBody(new byte[0]).build();
            doThrow(new OrderNotFoundException("Order not found with ID: null"))
                    .when(paymentService).processPayment(null);

            // Act & Assert
            assertThatThrownBy(() -> paymentConsumer.handlePaymentRequest(event, message))
                    .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        }
    }
}