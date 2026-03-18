package com.gozzerks.payflow.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Order Model Tests")
class OrderTest {

    @Nested
    @DisplayName("Constructor Tests")
    class ConstructorTests {

        @Test
        @DisplayName("Should create order with all-args constructor")
        void shouldCreateOrderWithAllArgsConstructor() {
            // Arrange
            BigDecimal amount = new BigDecimal("50.0000");
            String currency = "USD";
            String idempotencyKey = "test-key-123";

            // Act
            Order order = new Order(amount, currency, idempotencyKey);

            // Assert
            assertThat(order.getAmount()).isEqualByComparingTo(amount);
            assertThat(order.getCurrency()).isEqualTo(currency);
            assertThat(order.getIdempotencyKey()).isEqualTo(idempotencyKey);
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        }

        @Test
        @DisplayName("Should create order with default status PENDING using no-args constructor")
        void shouldCreateOrderWithDefaultStatus() {
            // Act
            Order order = new Order();

            // Assert
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        }
    }

    @Nested
    @DisplayName("equals() Tests")
    class EqualsTests {

        @Test
        @DisplayName("Should be equal to itself (same reference)")
        void shouldBeEqualToItself() {
            // Arrange
            Order order = new Order(new BigDecimal("10.00"), "GBP", "key-abc");

            // Act & Assert
            assertThat(order).isEqualTo(order);
        }

        @Test
        @DisplayName("Should be equal when idempotency keys match")
        void shouldBeEqualWhenIdempotencyKeysMatch() {
            // Arrange
            Order order1 = new Order(new BigDecimal("10.00"), "GBP", "same-key");
            Order order2 = new Order(new BigDecimal("99.99"), "USD", "same-key");

            // Act & Assert
            assertThat(order1).isEqualTo(order2);
        }

        @Test
        @DisplayName("Should not be equal when idempotency keys differ")
        void shouldNotBeEqualWhenIdempotencyKeysDiffer() {
            // Arrange
            Order order1 = new Order(new BigDecimal("10.00"), "GBP", "key-one");
            Order order2 = new Order(new BigDecimal("10.00"), "GBP", "key-two");

            // Act & Assert
            assertThat(order1).isNotEqualTo(order2);
        }

        @Test
        @DisplayName("Should not be equal to null")
        void shouldNotBeEqualToNull() {
            // Arrange
            Order order = new Order(new BigDecimal("10.00"), "GBP", "key-abc");

            // Act & Assert
            assertThat(order).isNotEqualTo(null);
        }

        @Test
        @DisplayName("Should not be equal to a different type")
        void shouldNotBeEqualToDifferentType() {
            // Arrange
            Order order = new Order(new BigDecimal("10.00"), "GBP", "key-abc");
            Object other = new Object();

            // Act & Assert
            assertThat(order).isNotEqualTo(other);
        }
    }

    @Nested
    @DisplayName("hashCode() Tests")
    class HashCodeTests {

        @Test
        @DisplayName("Should return same hashCode for equal orders")
        void shouldReturnSameHashCodeForEqualOrders() {
            // Arrange
            Order order1 = new Order(new BigDecimal("10.00"), "GBP", "same-key");
            Order order2 = new Order(new BigDecimal("99.99"), "USD", "same-key");

            // Act & Assert
            assertThat(order1.hashCode()).isEqualTo(order2.hashCode());
        }

        @Test
        @DisplayName("Should return consistent hashCode on repeated calls")
        void shouldReturnConsistentHashCode() {
            // Arrange
            Order order = new Order(new BigDecimal("10.00"), "GBP", "key-abc");

            // Act & Assert
            assertThat(order.hashCode()).isEqualTo(order.hashCode());
        }
    }
}