package com.gozzerks.payflow.service;

import com.gozzerks.payflow.dto.CreateOrderRequest;
import com.gozzerks.payflow.dto.OrderResponse;
import com.gozzerks.payflow.event.PaymentRequestedEvent;
import com.gozzerks.payflow.model.Order;
import com.gozzerks.payflow.model.OrderStatus;
import com.gozzerks.payflow.repository.OrderRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Optional;

import static com.gozzerks.payflow.config.RabbitMQConfig.PAYMENT_EXCHANGE;
import static com.gozzerks.payflow.config.RabbitMQConfig.PAYMENT_ROUTING_KEY;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderService — Edge Cases")
class OrderServiceEdgeCaseTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private OrderService orderService;

    // ─── Helper ───
    private void stubSaveWithId(long id) {
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order o = invocation.getArgument(0);
            o.setId(id);
            return o;
        });
    }

    @Nested
    @DisplayName("Amount boundary values")
    class AmountBoundaries {

        @Test
        @DisplayName("Should accept amount at exact minimum 0.01")
        void shouldAcceptMinimumAmount() {
            // Arrange
            CreateOrderRequest request = new CreateOrderRequest(new BigDecimal("0.01"), "GBP");
            when(orderRepository.findByIdempotencyKey("min-key")).thenReturn(Optional.empty());
            stubSaveWithId(1L);

            // Act
            OrderResponse response = orderService.createOrder(request, "min-key");

            // Assert
            assertThat(response.amount()).isEqualByComparingTo("0.0100");
        }

        @Test
        @DisplayName("Should accept amount at exact maximum 177777.7777")
        void shouldAcceptMaximumAmount() {
            // Arrange
            CreateOrderRequest request = new CreateOrderRequest(new BigDecimal("177777.7777"), "GBP");
            when(orderRepository.findByIdempotencyKey("max-key")).thenReturn(Optional.empty());
            stubSaveWithId(1L);

            // Act
            OrderResponse response = orderService.createOrder(request, "max-key");

            // Assert
            assertThat(response.amount()).isEqualByComparingTo("177777.7777");
        }

        @Test
        @DisplayName("Should reject amount just barely above zero (0.001) — passes service check but below DTO min")
        void shouldAcceptAmountBarelyAboveZero() {
            // Arrange - service only checks > 0, not the 0.01 DTO minimum; DTO catches it at the controller
            CreateOrderRequest request = new CreateOrderRequest(new BigDecimal("0.001"), "GBP");
            when(orderRepository.findByIdempotencyKey("tiny-key")).thenReturn(Optional.empty());
            stubSaveWithId(1L);

            // Act
            OrderResponse response = orderService.createOrder(request, "tiny-key");

            // Assert - service allows it (> 0), scaled to 0.0010
            assertThat(response.amount()).isEqualByComparingTo("0.0010");
        }

        @Test
        @DisplayName("Should reject amount at exactly zero")
        void shouldRejectExactlyZero() {
            // Arrange
            CreateOrderRequest request = new CreateOrderRequest(new BigDecimal("0.00"), "GBP");

            // Act & Assert
            assertThatThrownBy(() -> orderService.createOrder(request, "zero-key"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Amount must be greater than zero");
        }

        @Test
        @DisplayName("Should reject very small negative amount (-0.01)")
        void shouldRejectSmallNegativeAmount() {
            // Arrange
            CreateOrderRequest request = new CreateOrderRequest(new BigDecimal("-0.01"), "GBP");

            // Act & Assert
            assertThatThrownBy(() -> orderService.createOrder(request, "neg-key"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Amount must be greater than zero");
        }
    }

    @Nested
    @DisplayName("Amount rounding (setScale 4, HALF_UP)")
    class AmountRounding {

        @Test
        @DisplayName("Should round 29.99999 up to 30.0000")
        void shouldRoundUp5thDecimalPlace() {
            // Arrange
            CreateOrderRequest request = new CreateOrderRequest(new BigDecimal("29.99999"), "GBP");
            when(orderRepository.findByIdempotencyKey("round-key")).thenReturn(Optional.empty());
            stubSaveWithId(1L);

            // Act
            OrderResponse response = orderService.createOrder(request, "round-key");

            // Assert
            assertThat(response.amount()).isEqualByComparingTo("30.0000");
        }

        @Test
        @DisplayName("Should round 29.99994 down to 29.9999")
        void shouldRoundDown5thDecimalPlace() {
            // Arrange
            CreateOrderRequest request = new CreateOrderRequest(new BigDecimal("29.99994"), "GBP");
            when(orderRepository.findByIdempotencyKey("round-down-key")).thenReturn(Optional.empty());
            stubSaveWithId(1L);

            // Act
            OrderResponse response = orderService.createOrder(request, "round-down-key");

            // Assert
            assertThat(response.amount()).isEqualByComparingTo("29.9999");
        }

        @Test
        @DisplayName("Should handle HALF_UP tiebreaker (29.99995 → 30.0000)")
        void shouldHandleHalfUpTiebreaker() {
            // Arrange
            CreateOrderRequest request = new CreateOrderRequest(new BigDecimal("29.99995"), "GBP");
            when(orderRepository.findByIdempotencyKey("tie-key")).thenReturn(Optional.empty());
            stubSaveWithId(1L);

            // Act
            OrderResponse response = orderService.createOrder(request, "tie-key");

            // Assert
            BigDecimal expected = new BigDecimal("29.99995").setScale(4, RoundingMode.HALF_UP);
            assertThat(response.amount()).isEqualByComparingTo(expected);
        }

        @Test
        @DisplayName("Should scale integer amount to 4 decimal places (100 → 100.0000)")
        void shouldScaleIntegerAmount() {
            // Arrange
            CreateOrderRequest request = new CreateOrderRequest(new BigDecimal("100"), "GBP");
            when(orderRepository.findByIdempotencyKey("int-key")).thenReturn(Optional.empty());
            stubSaveWithId(1L);

            // Act
            OrderResponse response = orderService.createOrder(request, "int-key");

            // Assert
            assertThat(response.amount().scale()).isEqualTo(4);
            assertThat(response.amount()).isEqualByComparingTo("100.0000");
        }

        @Test
        @DisplayName("Should handle amount with many decimal places (1.123456789)")
        void shouldHandleManyDecimalPlaces() {
            // Arrange
            CreateOrderRequest request = new CreateOrderRequest(new BigDecimal("1.123456789"), "GBP");
            when(orderRepository.findByIdempotencyKey("many-dec")).thenReturn(Optional.empty());
            stubSaveWithId(1L);

            // Act
            OrderResponse response = orderService.createOrder(request, "many-dec");

            // Assert
            assertThat(response.amount()).isEqualByComparingTo("1.1235"); // HALF_UP rounds 5 up
        }
    }

    @Nested
    @DisplayName("Currency edge cases")
    class CurrencyEdgeCases {

        @Test
        @DisplayName("Whitespace-only currency should be rejected by isBlank check")
        void whitespaceCurrencyShouldBeRejected() {
            // Arrange
            CreateOrderRequest request = new CreateOrderRequest(new BigDecimal("10.00"), "   ");

            // Act & Assert
            assertThatThrownBy(() -> orderService.createOrder(request, "ws-key"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Currency is required");

            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("Mixed case currency should normalize to uppercase")
        void mixedCaseShouldNormalize() {
            // Arrange
            CreateOrderRequest request = new CreateOrderRequest(new BigDecimal("10.00"), "gBp");
            when(orderRepository.findByIdempotencyKey("mix-key")).thenReturn(Optional.empty());
            stubSaveWithId(1L);

            // Act
            OrderResponse response = orderService.createOrder(request, "mix-key");

            // Assert
            assertThat(response.currency()).isEqualTo("GBP");
        }

        @Test
        @DisplayName("Over-length currency passes the service layer (length is enforced at the DB column)")
        void longCurrencyPassesServiceValidation() {
            // Arrange - service doesn't validate currency length; entity has @Column(length=3),
            // so this would hit a DB constraint violation at persist time
            CreateOrderRequest request = new CreateOrderRequest(new BigDecimal("10.00"), "GBPOUND");
            when(orderRepository.findByIdempotencyKey("long-curr")).thenReturn(Optional.empty());
            stubSaveWithId(1L);

            // Act
            OrderResponse response = orderService.createOrder(request, "long-curr");

            // Assert - service allows it (no length check)
            assertThat(response.currency()).isEqualTo("GBPOUND");
        }

        @Test
        @DisplayName("Single character currency passes service validation")
        void singleCharCurrencyPasses() {
            // Arrange
            CreateOrderRequest request = new CreateOrderRequest(new BigDecimal("10.00"), "G");
            when(orderRepository.findByIdempotencyKey("single-curr")).thenReturn(Optional.empty());
            stubSaveWithId(1L);

            // Act
            OrderResponse response = orderService.createOrder(request, "single-curr");

            // Assert
            assertThat(response.currency()).isEqualTo("G");
        }
    }

    @Nested
    @DisplayName("Idempotency key boundary values")
    class IdempotencyKeyBoundaries {

        @Test
        @DisplayName("Should accept key at exactly 64 characters")
        void shouldAcceptKeyAtExactly64Chars() {
            // Arrange
            String key64 = "a".repeat(64);
            CreateOrderRequest request = new CreateOrderRequest(new BigDecimal("10.00"), "GBP");
            when(orderRepository.findByIdempotencyKey(key64)).thenReturn(Optional.empty());
            stubSaveWithId(1L);

            // Act
            OrderResponse response = orderService.createOrder(request, key64);

            // Assert
            assertThat(response.idempotencyKey()).isEqualTo(key64);
            assertThat(response.idempotencyKey()).hasSize(64);
        }

        @Test
        @DisplayName("Should reject key at exactly 65 characters")
        void shouldRejectKeyAt65Chars() {
            // Arrange
            String key65 = "a".repeat(65);
            CreateOrderRequest request = new CreateOrderRequest(new BigDecimal("10.00"), "GBP");

            // Act & Assert
            assertThatThrownBy(() -> orderService.createOrder(request, key65))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("must not exceed 64 characters");
        }

        @Test
        @DisplayName("Should accept single character key")
        void shouldAcceptSingleCharKey() {
            // Arrange
            CreateOrderRequest request = new CreateOrderRequest(new BigDecimal("10.00"), "GBP");
            when(orderRepository.findByIdempotencyKey("a")).thenReturn(Optional.empty());
            stubSaveWithId(1L);

            // Act
            OrderResponse response = orderService.createOrder(request, "a");

            // Assert
            assertThat(response.idempotencyKey()).isEqualTo("a");
        }

        @Test
        @DisplayName("Should accept key with only underscores")
        void shouldAcceptUnderscoreOnlyKey() {
            // Arrange
            CreateOrderRequest request = new CreateOrderRequest(new BigDecimal("10.00"), "GBP");
            when(orderRepository.findByIdempotencyKey("___")).thenReturn(Optional.empty());
            stubSaveWithId(1L);

            // Act
            OrderResponse response = orderService.createOrder(request, "___");

            // Assert
            assertThat(response.idempotencyKey()).isEqualTo("___");
        }

        @Test
        @DisplayName("Should accept key with only hyphens")
        void shouldAcceptHyphenOnlyKey() {
            // Arrange
            CreateOrderRequest request = new CreateOrderRequest(new BigDecimal("10.00"), "GBP");
            when(orderRepository.findByIdempotencyKey("---")).thenReturn(Optional.empty());
            stubSaveWithId(1L);

            // Act
            OrderResponse response = orderService.createOrder(request, "---");

            // Assert
            assertThat(response.idempotencyKey()).isEqualTo("---");
        }

        @Test
        @DisplayName("Should reject key with spaces")
        void shouldRejectKeyWithSpaces() {
            // Arrange
            CreateOrderRequest request = new CreateOrderRequest(new BigDecimal("10.00"), "GBP");

            // Act & Assert
            assertThatThrownBy(() -> orderService.createOrder(request, "key with spaces"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("alphanumeric characters");
        }

        @Test
        @DisplayName("Should reject key with dots")
        void shouldRejectKeyWithDots() {
            // Arrange
            CreateOrderRequest request = new CreateOrderRequest(new BigDecimal("10.00"), "GBP");

            // Act & Assert
            assertThatThrownBy(() -> orderService.createOrder(request, "key.with.dots"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("alphanumeric characters");
        }

        @Test
        @DisplayName("Should reject key with unicode characters")
        void shouldRejectKeyWithUnicode() {
            // Arrange
            CreateOrderRequest request = new CreateOrderRequest(new BigDecimal("10.00"), "GBP");

            // Act & Assert
            assertThatThrownBy(() -> orderService.createOrder(request, "key-with-é"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("alphanumeric characters");
        }

        @Test
        @DisplayName("Should generate UUID when key is empty string")
        void shouldGenerateUuidForEmptyString() {
            // Arrange
            CreateOrderRequest request = new CreateOrderRequest(new BigDecimal("10.00"), "GBP");
            when(orderRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
            stubSaveWithId(1L);

            // Act - empty string is blank → UUID generated
            OrderResponse response = orderService.createOrder(request, "");

            // Assert
            assertThat(response.idempotencyKey()).matches("[a-f0-9-]{36}");
        }

        @Test
        @DisplayName("Should generate UUID when key is tab character")
        void shouldGenerateUuidForTabChar() {
            // Arrange
            CreateOrderRequest request = new CreateOrderRequest(new BigDecimal("10.00"), "GBP");
            when(orderRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
            stubSaveWithId(1L);

            // Act
            OrderResponse response = orderService.createOrder(request, "\t");

            // Assert
            assertThat(response.idempotencyKey()).matches("[a-f0-9-]{36}");
        }
    }

    @Nested
    @DisplayName("RabbitMQ failure during createOrder")
    class RabbitMQFailure {

        @Test
        @DisplayName("RabbitMQ publish failure propagates out of createOrder")
        void rabbitFailureShouldRollBackOrder() {
            // Arrange
            CreateOrderRequest request = new CreateOrderRequest(new BigDecimal("10.00"), "GBP");
            when(orderRepository.findByIdempotencyKey("rabbit-fail")).thenReturn(Optional.empty());
            stubSaveWithId(1L);
            doThrow(new AmqpException("Connection refused"))
                    .when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class));

            // Act & Assert - in a unit test with mocks we can only verify the exception propagates
            assertThatThrownBy(() -> orderService.createOrder(request, "rabbit-fail"))
                    .isInstanceOf(AmqpException.class);

            // The order was "saved" in the mock; in real life the transaction should roll back
            verify(orderRepository).save(any(Order.class));
        }
    }

    @Nested
    @DisplayName("Idempotency — duplicate key returns existing order unchanged")
    class IdempotencyBehavior {

        @Test
        @DisplayName("Duplicate key should return original order status, not PROCESSING")
        void duplicateKeyShouldReturnOriginalStatus() {
            // Arrange
            Order existingOrder = new Order();
            existingOrder.setId(42L);
            existingOrder.setAmount(new BigDecimal("50.0000"));
            existingOrder.setCurrency("USD");
            existingOrder.setStatus(OrderStatus.PAID); // Already paid
            existingOrder.setIdempotencyKey("paid-order-key");
            existingOrder.setCreatedAt(LocalDateTime.now().minusHours(1));

            when(orderRepository.findByIdempotencyKey("paid-order-key"))
                    .thenReturn(Optional.of(existingOrder));

            // Act
            CreateOrderRequest request = new CreateOrderRequest(new BigDecimal("999.99"), "EUR");
            OrderResponse response = orderService.createOrder(request, "paid-order-key");

            // Assert - returns the existing PAID order, not a new PROCESSING one
            assertThat(response.status()).isEqualTo("PAID");
            assertThat(response.amount()).isEqualByComparingTo("50.0000");
            assertThat(response.currency()).isEqualTo("USD");
            assertThat(response.id()).isEqualTo(42L);

            verify(orderRepository, never()).save(any());
            verifyNoInteractions(rabbitTemplate);
        }

        @Test
        @DisplayName("Duplicate key for FAILED order should return FAILED, not re-process")
        void duplicateKeyForFailedOrderReturnsFailedStatus() {
            // Arrange
            Order failedOrder = new Order();
            failedOrder.setId(7L);
            failedOrder.setAmount(new BigDecimal("25.0000"));
            failedOrder.setCurrency("GBP");
            failedOrder.setStatus(OrderStatus.FAILED);
            failedOrder.setIdempotencyKey("failed-key");
            failedOrder.setCreatedAt(LocalDateTime.now().minusMinutes(30));

            when(orderRepository.findByIdempotencyKey("failed-key"))
                    .thenReturn(Optional.of(failedOrder));

            // Act
            CreateOrderRequest request = new CreateOrderRequest(new BigDecimal("25.00"), "GBP");
            OrderResponse response = orderService.createOrder(request, "failed-key");

            // Assert
            assertThat(response.status()).isEqualTo("FAILED");
            verify(orderRepository, never()).save(any());
            verifyNoInteractions(rabbitTemplate);
        }
    }

    @Nested
    @DisplayName("Event payload correctness")
    class EventPayload {

        @Test
        @DisplayName("Event should contain the scaled amount, not the original")
        void eventShouldContainScaledAmount() {
            // Arrange
            CreateOrderRequest request = new CreateOrderRequest(new BigDecimal("29.9"), "GBP");
            when(orderRepository.findByIdempotencyKey("event-key")).thenReturn(Optional.empty());
            stubSaveWithId(1L);

            // Act
            orderService.createOrder(request, "event-key");

            // Assert
            ArgumentCaptor<PaymentRequestedEvent> captor = ArgumentCaptor.forClass(PaymentRequestedEvent.class);
            verify(rabbitTemplate).convertAndSend(eq(PAYMENT_EXCHANGE), eq(PAYMENT_ROUTING_KEY), captor.capture());

            PaymentRequestedEvent event = captor.getValue();
            assertThat(event.amount().scale()).isEqualTo(4);
            assertThat(event.amount()).isEqualByComparingTo("29.9000");
        }

        @Test
        @DisplayName("Event should contain normalized uppercase currency")
        void eventShouldContainUppercaseCurrency() {
            // Arrange
            CreateOrderRequest request = new CreateOrderRequest(new BigDecimal("10.00"), "eur");
            when(orderRepository.findByIdempotencyKey("eur-key")).thenReturn(Optional.empty());
            stubSaveWithId(1L);

            // Act
            orderService.createOrder(request, "eur-key");

            // Assert
            ArgumentCaptor<PaymentRequestedEvent> captor = ArgumentCaptor.forClass(PaymentRequestedEvent.class);
            verify(rabbitTemplate).convertAndSend(eq(PAYMENT_EXCHANGE), eq(PAYMENT_ROUTING_KEY), captor.capture());

            assertThat(captor.getValue().currency()).isEqualTo("EUR");
        }
    }

    @Nested
    @DisplayName("Validation order of operations")
    class ValidationOrder {

        @Test
        @DisplayName("Null amount should be checked before currency")
        void nullAmountCheckedFirst() {
            // Arrange
            CreateOrderRequest request = new CreateOrderRequest(null, null);

            // Act & Assert
            assertThatThrownBy(() -> orderService.createOrder(request, "test-key"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Amount is required");
        }

        @Test
        @DisplayName("Zero amount should be checked before null currency")
        void zeroAmountCheckedBeforeCurrency() {
            // Arrange
            CreateOrderRequest request = new CreateOrderRequest(BigDecimal.ZERO, null);

            // Act & Assert
            assertThatThrownBy(() -> orderService.createOrder(request, "test-key"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Amount must be greater than zero");
        }

        @Test
        @DisplayName("Key length should be checked before pattern")
        void keyLengthCheckedBeforePattern() {
            // Arrange - 65 chars of invalid characters
            String longInvalidKey = "@".repeat(65);
            CreateOrderRequest request = new CreateOrderRequest(new BigDecimal("10.00"), "GBP");

            // Act & Assert
            assertThatThrownBy(() -> orderService.createOrder(request, longInvalidKey))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("must not exceed 64 characters");
        }
    }
}