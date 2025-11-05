package com.gozzerks.payflow.service;

import com.gozzerks.payflow.dto.CreateOrderRequest;
import com.gozzerks.payflow.dto.OrderResponse;
import com.gozzerks.payflow.exception.OrderNotFoundException;
import com.gozzerks.payflow.model.Order;
import com.gozzerks.payflow.repository.OrderRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class OrderService {
    private final OrderRepository orderRepository;

    public OrderService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request, String idempotencyKey) {
        Optional<Order> existingOrder = orderRepository.findByIdempotencyKey(idempotencyKey);
        if (existingOrder.isPresent()) {
            return toResponse(existingOrder.get());
        }

        if (request.amount().compareTo(java.math.BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be greater than 0");
        }
        if (!"GBP".equalsIgnoreCase(request.currency())) {
            throw new IllegalArgumentException("Only GBP is supported");
        }

        Order order = new Order(request.amount(), request.currency(), idempotencyKey);
        Order saved = orderRepository.save(order);
        return toResponse(saved);
    }

    public OrderResponse getOrderById(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException("Order not found: " + id));
        return toResponse(order);
    }

    private OrderResponse toResponse(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getAmount(),
                order.getCurrency(),
                order.getStatus().name(),
                order.getIdempotencyKey(),
                order.getCreatedAt()
        );
    }
}