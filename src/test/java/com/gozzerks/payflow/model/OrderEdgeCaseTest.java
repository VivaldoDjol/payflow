package com.gozzerks.payflow.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Order Model — Edge Cases")
class OrderEdgeCaseTest {

    @Nested
    @DisplayName("Default idempotency key generation")
    class DefaultKeyGeneration {

        @Test
        @DisplayName("No-args constructor should auto-generate a UUID idempotency key")
        void noArgsConstructorShouldGenerateKey() {
            // Arrange
            Order order = new Order();

            // Act & Assert
            assertThat(order.getIdempotencyKey()).isNotNull();
            assertThat(order.getIdempotencyKey()).isNotBlank();
        }

        @Test
        @DisplayName("Two no-args orders should have different auto-generated keys")
        void twoNoArgsOrdersShouldHaveDifferentKeys() {
            // Arrange
            Order order1 = new Order();
            Order order2 = new Order();

            // Act & Assert
            assertThat(order1.getIdempotencyKey()).isNotEqualTo(order2.getIdempotencyKey());
        }

        @Test
        @DisplayName("Two no-args orders should NOT be equal (unique generated keys)")
        void twoNoArgsOrdersShouldNotBeEqual() {
            // Arrange
            Order order1 = new Order();
            Order order2 = new Order();

            // Act & Assert
            assertThat(order1).isNotEqualTo(order2);
        }

        @Test
        @DisplayName("Two no-args orders should have different hash codes")
        void twoNoArgsOrdersShouldHaveDifferentHashCodes() {
            // Arrange
            Order order1 = new Order();
            Order order2 = new Order();

            // Act & Assert
            assertThat(order1.hashCode()).isNotEqualTo(order2.hashCode());
        }

        @Test
        @DisplayName("Constructor with null key should auto-generate a UUID")
        void constructorWithNullKeyShouldGenerateKey() {
            // Arrange
            Order order = new Order(new BigDecimal("10.00"), "GBP", null);

            // Act & Assert
            assertThat(order.getIdempotencyKey()).isNotNull();
            assertThat(order.getIdempotencyKey()).isNotBlank();
        }

        @Test
        @DisplayName("Constructor with explicit key should use it, not generate")
        void constructorWithExplicitKeyShouldUseIt() {
            // Arrange
            Order order = new Order(new BigDecimal("10.00"), "GBP", "my-key");

            // Act & Assert
            assertThat(order.getIdempotencyKey()).isEqualTo("my-key");
        }
    }

    @Nested
    @DisplayName("Default field values")
    class DefaultValues {

        @Test
        @DisplayName("New order should have non-null createdAt timestamp")
        void newOrderShouldHaveCreatedAt() {
            // Arrange
            Instant before = Instant.now();
            Order order = new Order();
            Instant after = Instant.now();

            // Act & Assert
            assertThat(order.getCreatedAt()).isBetween(before, after);
        }

        @Test
        @DisplayName("New order should have PENDING status by default")
        void newOrderShouldHavePendingStatus() {
            // Arrange
            Order order = new Order();

            // Act & Assert
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        }

        @Test
        @DisplayName("New order ID should be null before persistence")
        void newOrderIdShouldBeNull() {
            // Arrange
            Order order = new Order();

            // Act & Assert
            assertThat(order.getId()).isNull();
        }
    }

    @Nested
    @DisplayName("Setter edge cases")
    class SetterEdgeCases {

        @Test
        @DisplayName("Setting status to null should be allowed by setter")
        void settingStatusToNullShouldWork() {
            // Arrange
            Order order = new Order();

            // Act
            order.setStatus(null);

            // Assert
            assertThat(order.getStatus()).isNull();
        }

        @Test
        @DisplayName("Setting amount to null should be allowed by setter")
        void settingAmountToNullShouldWork() {
            // Arrange
            Order order = new Order(new BigDecimal("10.00"), "GBP", "key-1");

            // Act
            order.setAmount(null);

            // Assert
            assertThat(order.getAmount()).isNull();
        }

        @Test
        @DisplayName("Setting currency to null should be allowed by setter")
        void settingCurrencyToNullShouldWork() {
            // Arrange
            Order order = new Order(new BigDecimal("10.00"), "GBP", "key-1");

            // Act
            order.setCurrency(null);

            // Assert
            assertThat(order.getCurrency()).isNull();
        }

        @Test
        @DisplayName("Setting idempotencyKey to null should be allowed by setter")
        void settingIdempotencyKeyToNullShouldWork() {
            // Arrange
            Order order = new Order(new BigDecimal("10.00"), "GBP", "key-1");

            // Act
            order.setIdempotencyKey(null);

            // Assert
            assertThat(order.getIdempotencyKey()).isNull();
        }
    }

    @Nested
    @DisplayName("Status transition coverage")
    class StatusTransitions {

        @Test
        @DisplayName("Order can transition through all statuses without restriction")
        void orderCanTransitionThroughAllStatuses() {
            // Arrange
            Order order = new Order();
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);

            // Act & Assert
            order.setStatus(OrderStatus.PROCESSING);
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PROCESSING);

            order.setStatus(OrderStatus.PAID);
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);

            // The entity enforces no transition rules - PAID can move to FAILED
            order.setStatus(OrderStatus.FAILED);
            assertThat(order.getStatus()).isEqualTo(OrderStatus.FAILED);

            // ...and FAILED back to PENDING (any status guard lives in the service, not the entity)
            order.setStatus(OrderStatus.PENDING);
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        }
    }

    @Nested
    @DisplayName("BigDecimal amount precision")
    class AmountPrecision {

        @Test
        @DisplayName("Amount with high precision should retain exact value in entity")
        void amountWithHighPrecisionShouldRetain() {
            // Arrange
            Order order = new Order();
            BigDecimal precise = new BigDecimal("123.456789012345");

            // Act
            order.setAmount(precise);

            // Assert
            assertThat(order.getAmount()).isEqualByComparingTo(precise);
        }

        @Test
        @DisplayName("Amount at exact max boundary 177777.7777")
        void amountAtMaxBoundary() {
            // Arrange
            Order order = new Order();

            // Act
            order.setAmount(new BigDecimal("177777.7777"));

            // Assert
            assertThat(order.getAmount()).isEqualByComparingTo("177777.7777");
        }

        @Test
        @DisplayName("Amount at exact min boundary 0.01")
        void amountAtMinBoundary() {
            // Arrange
            Order order = new Order();

            // Act
            order.setAmount(new BigDecimal("0.01"));

            // Assert
            assertThat(order.getAmount()).isEqualByComparingTo("0.01");
        }
    }

    @Nested
    @DisplayName("equals() and hashCode() symmetry")
    class EqualsHashCodeSymmetry {

        @Test
        @DisplayName("Equals should be symmetric")
        void equalsShouldBeSymmetric() {
            // Arrange
            Order a = new Order(new BigDecimal("10"), "GBP", "same-key");
            Order b = new Order(new BigDecimal("20"), "USD", "same-key");

            // Act & Assert
            assertThat(a.equals(b)).isEqualTo(b.equals(a));
        }

        @Test
        @DisplayName("Equals should be transitive")
        void equalsShouldBeTransitive() {
            // Arrange
            Order a = new Order(new BigDecimal("10"), "GBP", "same-key");
            Order b = new Order(new BigDecimal("20"), "USD", "same-key");
            Order c = new Order(new BigDecimal("30"), "EUR", "same-key");

            // Act & Assert
            assertThat(a).isEqualTo(b);
            assertThat(b).isEqualTo(c);
            assertThat(a).isEqualTo(c);
        }

        @Test
        @DisplayName("Unequal orders should have different hash codes (not guaranteed but likely)")
        void unequalOrdersShouldLikelyHaveDifferentHashes() {
            // Arrange
            Order a = new Order(new BigDecimal("10"), "GBP", "key-alpha");
            Order b = new Order(new BigDecimal("10"), "GBP", "key-beta");

            // Act & Assert
            // Not strictly required by contract, but a good hash should minimize collisions
            assertThat(a.hashCode()).isNotEqualTo(b.hashCode());
        }
    }
}