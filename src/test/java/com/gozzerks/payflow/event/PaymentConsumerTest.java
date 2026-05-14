package com.gozzerks.payflow.event;

import com.gozzerks.payflow.config.RabbitMQConfig;
import com.gozzerks.payflow.exception.PaymentGatewayException;
import com.gozzerks.payflow.service.PaymentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentConsumer Tests")
class PaymentConsumerTest {

    @Mock
    private PaymentService paymentService;

    @InjectMocks
    private PaymentConsumer paymentConsumer;

    @Test
    @DisplayName("Should process payment request successfully")
    void shouldProcessPaymentRequestSuccessfully() {
        PaymentRequestedEvent event = new PaymentRequestedEvent(
                1L, new BigDecimal("29.99"), "GBP", "test-key-123");
        Message message = MessageBuilder.withBody(new byte[0]).build();

        doNothing().when(paymentService).processPayment(1L);

        paymentConsumer.handlePaymentRequest(event, message);

        verify(paymentService, times(1)).processPayment(1L);
    }

    @Test
    @DisplayName("Should handle payment processing failure and route to DLQ")
    void shouldHandlePaymentProcessingFailure() {
        PaymentRequestedEvent event = new PaymentRequestedEvent(
                1L, new BigDecimal("29.99"), "GBP", "test-key-123");
        Message message = MessageBuilder.withBody(new byte[0]).build();

        doThrow(new PaymentGatewayException("Payment gateway error"))
                .when(paymentService).processPayment(1L);

        assertThatThrownBy(() -> paymentConsumer.handlePaymentRequest(event, message))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class)
                .hasCauseInstanceOf(PaymentGatewayException.class)
                .cause().hasMessageContaining("Payment gateway error");

        verify(paymentService, times(1)).processPayment(1L);
    }

    @Test
    @DisplayName("Should extract correct order ID from event")
    void shouldExtractCorrectOrderIdFromEvent() {
        Long expectedOrderId = 42L;
        PaymentRequestedEvent event = new PaymentRequestedEvent(
                expectedOrderId, new BigDecimal("100.00"), "USD", "test-key-456");
        Message message = MessageBuilder.withBody(new byte[0]).build();

        doNothing().when(paymentService).processPayment(expectedOrderId);

        paymentConsumer.handlePaymentRequest(event, message);

        verify(paymentService).processPayment(expectedOrderId);
        verifyNoMoreInteractions(paymentService);
    }

    @Test
    @DisplayName("Should wrap PaymentGatewayException with cause in AmqpRejectAndDontRequeueException")
    void shouldWrapExceptionWithCause() {
        PaymentRequestedEvent event = new PaymentRequestedEvent(
                1L, new BigDecimal("29.99"), "GBP", "test-key-123");
        Message message = MessageBuilder.withBody(new byte[0]).build();
        Throwable cause = new RuntimeException("upstream error");

        doThrow(new PaymentGatewayException("Payment gateway error", cause))
                .when(paymentService).processPayment(1L);

        assertThatThrownBy(() -> paymentConsumer.handlePaymentRequest(event, message))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class)
                .hasCauseInstanceOf(PaymentGatewayException.class);

        verify(paymentService).processPayment(1L);
    }

    @Test
    @DisplayName("Should treat x-death entry with null count as zero retries")
    void shouldTreatNullCountInXDeathAsZeroRetries() {
        PaymentRequestedEvent event = new PaymentRequestedEvent(
                1L, new BigDecimal("29.99"), "GBP", "test-key-123");
        Message message = MessageBuilder.withBody(new byte[0]).build();
        Map<String, Object> entry = new java.util.HashMap<>();
        entry.put("queue", RabbitMQConfig.PAYMENT_QUEUE);
        entry.put("reason", "rejected");
        entry.put("count", null);
        message.getMessageProperties().getHeaders().put("x-death", List.of(entry));

        paymentConsumer.handlePaymentRequest(event, message);

        verify(paymentService).processPayment(1L);
    }

    @Test
    @DisplayName("Should treat non-null but empty x-death list as zero retries")
    void shouldTreatEmptyXDeathAsZeroRetries() {
        PaymentRequestedEvent event = new PaymentRequestedEvent(
                1L, new BigDecimal("29.99"), "GBP", "test-key-123");
        Message message = MessageBuilder.withBody(new byte[0]).build();
        message.getMessageProperties().getHeaders().put("x-death", List.of());

        paymentConsumer.handlePaymentRequest(event, message);

        verify(paymentService).processPayment(1L);
    }

    @Test
    @DisplayName("Should mark order as FAILED after max retries exceeded")
    void shouldMarkAsFailedAfterMaxRetries() {
        PaymentRequestedEvent event = new PaymentRequestedEvent(
                1L, new BigDecimal("29.99"), "GBP", "test-key-123");

        Message message = MessageBuilder.withBody(new byte[0]).build();
        message.getMessageProperties().getHeaders().put("x-death", List.of(
                Map.of("queue", RabbitMQConfig.PAYMENT_QUEUE,
                        "reason", "rejected",
                        "count", (long) RabbitMQConfig.MAX_RETRIES)
        ));

        paymentConsumer.handlePaymentRequest(event, message);

        verify(paymentService).markAsFailed(1L);
        verify(paymentService, never()).processPayment(anyLong());
    }
}
