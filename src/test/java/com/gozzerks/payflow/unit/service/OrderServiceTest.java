package com.gozzerks.payflow.unit.service;

import com.gozzerks.payflow.dto.CreateOrderRequest;
import com.gozzerks.payflow.dto.OrderResponse;
import com.gozzerks.payflow.model.Order;
import com.gozzerks.payflow.repository.OrderRepository;
import com.gozzerks.payflow.service.OrderService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private OrderService orderService;

    @Test
    @DisplayName("should create order with correct details and idempotency key")
    void shouldCreateOrder() {
        CreateOrderRequest request = new CreateOrderRequest(new BigDecimal("29.99"), "GBP");
        String idempotencyKey = "key123";

        when(orderRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            order.setId(1L);
            return order;
        });

        LocalDateTime before = LocalDateTime.now();
        OrderResponse response = orderService.createOrder(request, idempotencyKey);
        LocalDateTime after = LocalDateTime.now();

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.amount()).isEqualByComparingTo("29.99");
        assertThat(response.currency()).isEqualTo("GBP");
        assertThat(response.status()).isEqualTo("PROCESSING");
        assertThat(response.idempotencyKey()).isEqualTo(idempotencyKey);
        assertThat(response.createdAt()).isBetween(before, after);

        verify(orderRepository).findByIdempotencyKey(idempotencyKey);
        verify(orderRepository).save(any(Order.class));
        verify(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class));
    }
}