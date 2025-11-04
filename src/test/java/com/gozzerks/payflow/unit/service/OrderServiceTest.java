package com.gozzerks.payflow.unit.service;

import com.gozzerks.payflow.dto.CreateOrderRequest;
import com.gozzerks.payflow.dto.OrderResponse;
import com.gozzerks.payflow.model.Order;
import com.gozzerks.payflow.model.OrderStatus;
import com.gozzerks.payflow.repository.OrderRepository;
import com.gozzerks.payflow.service.OrderService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private OrderService orderService;

    @Test
    @DisplayName("should create order with correct details and idempotency key")
    void shouldCreateOrder() {
        CreateOrderRequest request = new CreateOrderRequest(new BigDecimal("29.99"), "GBP");
        String idempotencyKey = "key123";

        LocalDateTime before = LocalDateTime.now();
        Order savedOrder = new Order(request.amount(), request.currency(), idempotencyKey);
        LocalDateTime after = LocalDateTime.now();

        savedOrder.setId(1L);
        savedOrder.setStatus(OrderStatus.PENDING);

        when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);

        OrderResponse response = orderService.createOrder(request, idempotencyKey);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.amount()).isEqualByComparingTo("29.99");
        assertThat(response.currency()).isEqualTo("GBP");
        assertThat(response.status()).isEqualTo("PENDING");
        assertThat(response.idempotencyKey()).isEqualTo(idempotencyKey);
        assertThat(response.createdAt()).isBetween(before, after);

        verify(orderRepository).save(any(Order.class));
    }
}