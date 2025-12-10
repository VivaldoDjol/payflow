package com.gozzerks.payflow.service;

import com.gozzerks.payflow.dto.CreateOrderRequest;
import com.gozzerks.payflow.dto.OrderResponse;
import com.gozzerks.payflow.event.PaymentRequestedEvent;
import com.gozzerks.payflow.exception.OrderNotFoundException;
import com.gozzerks.payflow.model.Order;
import com.gozzerks.payflow.model.OrderStatus;
import com.gozzerks.payflow.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.regex.Pattern;

import static com.gozzerks.payflow.config.RabbitMQConfig.PAYMENT_EXCHANGE;
import static com.gozzerks.payflow.config.RabbitMQConfig.PAYMENT_ROUTING_KEY;

@Service
@Transactional
public class OrderService {
    private static final Logger log = LoggerFactory.getLogger(OrderService.class);
    private static final Pattern IDEMPOTENCY_KEY_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]+$");
    private final OrderRepository orderRepository;
    private final RabbitTemplate rabbitTemplate;

    public OrderService(OrderRepository orderRepository, RabbitTemplate rabbitTemplate) {
        this.orderRepository = orderRepository;
        this.rabbitTemplate = rabbitTemplate;
    }

    public OrderResponse createOrder(CreateOrderRequest request, String idempotencyKey) {
        if (request.amount() == null) {
            throw new IllegalArgumentException("Amount is required");
        }

        if (request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }

        if (request.currency() == null || request.currency().isEmpty()) {
            throw new IllegalArgumentException("Currency is required");
        }

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            idempotencyKey = UUID.randomUUID().toString();
        }

        if (!IDEMPOTENCY_KEY_PATTERN.matcher(idempotencyKey).matches()) {
            throw new IllegalArgumentException("Idempotency key can only contain alphanumeric characters, underscores, and hyphens");
        }

        final String finalIdempotencyKey = idempotencyKey;

        return orderRepository.findByIdempotencyKey(idempotencyKey)
                .map(this::toOrderResponse)
                .orElseGet(() -> {
                    Order order = new Order();
                    order.setAmount(request.amount().setScale(4, RoundingMode.HALF_UP));
                    order.setCurrency(request.currency().toUpperCase());
                    order.setStatus(OrderStatus.PROCESSING);
                    order.setIdempotencyKey(finalIdempotencyKey);
                    order.setCreatedAt(LocalDateTime.now());

                    Order savedOrder = orderRepository.save(order);

                    PaymentRequestedEvent event = new PaymentRequestedEvent(
                            savedOrder.getId(),
                            savedOrder.getAmount(),
                            savedOrder.getCurrency(),
                            savedOrder.getIdempotencyKey()
                    );

                    rabbitTemplate.convertAndSend(PAYMENT_EXCHANGE, PAYMENT_ROUTING_KEY, event);
                    log.info("Payment requested for order ID: {}", savedOrder.getId());

                    return toOrderResponse(savedOrder);
                });
    }

    public OrderResponse getOrderById(Long id) {
        return orderRepository.findById(id)
                .map(this::toOrderResponse)
                .orElseThrow(() -> new OrderNotFoundException("Order not found with ID: " + id));
    }

    private OrderResponse toOrderResponse(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getAmount(),
                order.getCurrency(),
                order.getStatus().toString(),
                order.getIdempotencyKey(),
                order.getCreatedAt()
        );
    }
}