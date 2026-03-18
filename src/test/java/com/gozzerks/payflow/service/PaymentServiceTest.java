package com.gozzerks.payflow.service;

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
@DisplayName("PaymentService Tests")
class PaymentServiceTest {

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

    @Nested
    @DisplayName("Process Payment Tests")
    class ProcessPaymentTests {

        @Test
        @DisplayName("Should mark order as PAID when payment succeeds")
        void shouldMarkOrderAsPaidWhenPaymentSucceeds() {
            // Arrange
            Long orderId = 1L;
            Order order = new Order();
            order.setId(orderId);
            order.setAmount(new BigDecimal("100.0000"));
            order.setCurrency("USD");
            order.setIdempotencyKey("test-key-pay-success");
            order.setStatus(OrderStatus.PROCESSING);

            when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
            when(mockRandom.nextInt(10)).thenReturn(5); // non-zero → success
            when(orderRepository.save(any(Order.class))).thenReturn(order);

            // Act
            paymentService.processPayment(orderId);

            // Assert
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
            verify(orderRepository).save(order);
        }

        @Test
        @DisplayName("Should mark order as FAILED and throw exception when payment fails")
        void shouldMarkOrderAsFailedAndThrowWhenPaymentFails() {
            // Arrange
            Long orderId = 2L;
            Order order = new Order();
            order.setId(orderId);
            order.setAmount(new BigDecimal("50.0000"));
            order.setCurrency("EUR");
            order.setIdempotencyKey("test-key-pay-fail");
            order.setStatus(OrderStatus.PROCESSING);

            when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
            when(mockRandom.nextInt(10)).thenReturn(0); // zero → failure

            // Act & Assert
            assertThatThrownBy(() -> paymentService.processPayment(orderId))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Payment gateway error");

            assertThat(order.getStatus()).isEqualTo(OrderStatus.FAILED);
        }

        @Test
        @DisplayName("Should throw RuntimeException when order is not found")
        void shouldThrowWhenOrderNotFound() {
            // Arrange
            Long nonExistentId = 999L;
            when(orderRepository.findById(nonExistentId)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> paymentService.processPayment(nonExistentId))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Order not found: 999");

            verify(orderRepository, never()).save(any());
            verifyNoMoreInteractions(mockRandom);
        }
    }
}