package com.gozzerks.payflow.service;

import com.gozzerks.payflow.dto.CreateOrderRequest;
import com.gozzerks.payflow.dto.OrderResponse;
import com.gozzerks.payflow.event.PaymentRequestedEvent;
import com.gozzerks.payflow.model.Order;
import com.gozzerks.payflow.model.OrderStatus;
import com.gozzerks.payflow.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.RoundingMode;

@Service
public class OrderService {
    private static final Logger log = LoggerFactory.getLogger(OrderService.class);
    private final OrderRepository orderRepository;
    private final RabbitTemplate rabbitTemplate;

    public OrderService(OrderRepository orderRepository, RabbitTemplate rabbitTemplate) {
        this.orderRepository = orderRepository;
        this.rabbitTemplate = rabbitTemplate;
    }

    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request, String idempotencyKey) {
        var existing = orderRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            log.info("Idempotency key {} reused, returning existing order {}", idempotencyKey, existing.get().getId());
            return toResponse(existing.get());
        }

        Order order = new Order(request.amount(), request.currency(), idempotencyKey);
        Order saved = orderRepository.save(order);

        rabbitTemplate.convertAndSend(
                "payment.exchange",
                "payment.routing.key",
                new PaymentRequestedEvent(
                        saved.getId(),
                        saved.getAmount().setScale(4, RoundingMode.HALF_UP),
                        saved.getCurrency(),
                        saved.getIdempotencyKey()
                )
        );

        saved.setStatus(OrderStatus.PROCESSING);

        return toResponse(saved);
    }

    public OrderResponse getOrderById(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Order not found: " + id));
        return toResponse(order);
    }

    private OrderResponse toResponse(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getAmount().setScale(4, RoundingMode.HALF_UP),
                order.getCurrency(),
                order.getStatus().name(),
                order.getIdempotencyKey(),
                order.getCreatedAt()
        );
    }
}