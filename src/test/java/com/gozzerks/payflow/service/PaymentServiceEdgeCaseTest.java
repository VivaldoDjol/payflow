package com.gozzerks.payflow.service;

import com.gozzerks.payflow.exception.PaymentGatewayException;
import com.gozzerks.payflow.model.Order;
import com.gozzerks.payflow.model.OrderStatus;
import com.gozzerks.payflow.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentService — Edge Cases")
class PaymentServiceEdgeCaseTest {

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private PaymentService paymentService;

    @Mock
    private Random mockRandom;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(paymentService, "random", mockRandom);
    }

    private Order orderInStatus(Long id, OrderStatus status) {
        Order order = new Order();
        order.setId(id);
        order.setAmount(new BigDecimal("50.0000"));
        order.setCurrency("GBP");
        order.setIdempotencyKey("key-" + id);
        order.setStatus(status);
        return order;
    }

    @Nested
    @DisplayName("Terminal state protection — processPayment")
    class TerminalStateProcessPayment {

        @Test
        @DisplayName("processPayment on PAID order should be a no-op")
        void processPaymentOnPaidOrderShouldBeNoOp() {
            // Arrange
            Order paidOrder = orderInStatus(1L, OrderStatus.PAID);
            when(orderRepository.findById(1L)).thenReturn(Optional.of(paidOrder));

            // Act
            paymentService.processPayment(1L);

            // Assert
            assertThat(paidOrder.getStatus()).isEqualTo(OrderStatus.PAID);
            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("processPayment on FAILED order should be a no-op")
        void processPaymentOnFailedOrderShouldBeNoOp() {
            // Arrange
            Order failedOrder = orderInStatus(2L, OrderStatus.FAILED);
            when(orderRepository.findById(2L)).thenReturn(Optional.of(failedOrder));

            // Act
            paymentService.processPayment(2L);

            // Assert
            assertThat(failedOrder.getStatus()).isEqualTo(OrderStatus.FAILED);
            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("processPayment on PENDING order transitions to PAID on success")
        void processPaymentOnPendingOrderTransitionsToPaid() {
            // Arrange
            Order pendingOrder = orderInStatus(3L, OrderStatus.PENDING);
            when(orderRepository.findById(3L)).thenReturn(Optional.of(pendingOrder));
            when(mockRandom.nextInt(10)).thenReturn(1); // success
            when(orderRepository.save(any(Order.class))).thenReturn(pendingOrder);

            // Act
            paymentService.processPayment(3L);

            // Assert
            assertThat(pendingOrder.getStatus()).isEqualTo(OrderStatus.PAID);
        }
    }

    @Nested
    @DisplayName("Terminal state protection — markAsFailed")
    class TerminalStateMarkAsFailed {

        @Test
        @DisplayName("markAsFailed on PAID order should be a no-op")
        void markAsFailedOnPaidOrderShouldBeNoOp() {
            // Arrange
            Order paidOrder = orderInStatus(1L, OrderStatus.PAID);
            when(orderRepository.findById(1L)).thenReturn(Optional.of(paidOrder));

            // Act
            paymentService.markAsFailed(1L);

            // Assert
            assertThat(paidOrder.getStatus()).isEqualTo(OrderStatus.PAID);
            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("markAsFailed on already FAILED order is idempotent")
        void markAsFailedOnFailedOrderIsIdempotent() {
            // Arrange
            Order failedOrder = orderInStatus(2L, OrderStatus.FAILED);
            when(orderRepository.findById(2L)).thenReturn(Optional.of(failedOrder));
            when(orderRepository.save(any(Order.class))).thenReturn(failedOrder);

            // Act
            paymentService.markAsFailed(2L);

            // Assert
            assertThat(failedOrder.getStatus()).isEqualTo(OrderStatus.FAILED);
            verify(orderRepository).save(failedOrder);
        }

        @Test
        @DisplayName("markAsFailed on PENDING order transitions to FAILED")
        void markAsFailedOnPendingOrder() {
            // Arrange
            Order pendingOrder = orderInStatus(3L, OrderStatus.PENDING);
            when(orderRepository.findById(3L)).thenReturn(Optional.of(pendingOrder));
            when(orderRepository.save(any(Order.class))).thenReturn(pendingOrder);

            // Act
            paymentService.markAsFailed(3L);

            // Assert
            assertThat(pendingOrder.getStatus()).isEqualTo(OrderStatus.FAILED);
        }
    }

    @Nested
    @DisplayName("Payment failure does not save — only success saves")
    class SaveBehavior {

        @Test
        @DisplayName("On failure, exception thrown without saving — status stays PROCESSING")
        void failureDoesNotCallSave() {
            // Arrange
            Order order = orderInStatus(1L, OrderStatus.PROCESSING);
            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
            when(mockRandom.nextInt(10)).thenReturn(0); // failure

            // Act & Assert
            assertThatThrownBy(() -> paymentService.processPayment(1L))
                    .isInstanceOf(PaymentGatewayException.class);

            // Status stays PROCESSING — the exception is thrown without changing status
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PROCESSING);
            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("On success, order status is PAID and save IS called")
        void successCallsSave() {
            // Arrange
            Order order = orderInStatus(1L, OrderStatus.PROCESSING);
            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
            when(mockRandom.nextInt(10)).thenReturn(5); // success
            when(orderRepository.save(any(Order.class))).thenReturn(order);

            // Act
            paymentService.processPayment(1L);

            // Assert
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
            verify(orderRepository).save(order);
        }
    }

    @Nested
    @DisplayName("Edge case: repository failures")
    class RepositoryFailures {

        @Test
        @DisplayName("processPayment should propagate DB exception on findById failure")
        void processPaymentPropagatesDbException() {
            // Arrange
            when(orderRepository.findById(1L)).thenThrow(new RuntimeException("DB connection lost"));

            // Act & Assert
            assertThatThrownBy(() -> paymentService.processPayment(1L))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("DB connection lost");
        }

        @Test
        @DisplayName("processPayment should propagate DB exception on save failure")
        void processPaymentPropagatesDbSaveException() {
            // Arrange
            Order order = orderInStatus(1L, OrderStatus.PROCESSING);
            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
            when(mockRandom.nextInt(10)).thenReturn(5); // success path
            when(orderRepository.save(any())).thenThrow(new RuntimeException("Disk full"));

            // Act & Assert
            assertThatThrownBy(() -> paymentService.processPayment(1L))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Disk full");
        }

        @Test
        @DisplayName("markAsFailed should propagate DB exception on findById failure")
        void markAsFailedPropagatesDbException() {
            // Arrange
            when(orderRepository.findById(1L)).thenThrow(new RuntimeException("Timeout"));

            // Act & Assert
            assertThatThrownBy(() -> paymentService.markAsFailed(1L))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Timeout");
        }
    }
}