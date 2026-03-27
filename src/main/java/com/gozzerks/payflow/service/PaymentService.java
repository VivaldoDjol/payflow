package com.gozzerks.payflow.service;

import com.gozzerks.payflow.exception.PaymentGatewayException;
import com.gozzerks.payflow.model.Order;
import com.gozzerks.payflow.model.OrderStatus;
import com.gozzerks.payflow.repository.OrderRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Random;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    private final OrderRepository orderRepository;
    private final Random random = new Random();

    public PaymentService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Transactional
    public void markAsFailed(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found: " + orderId));
        order.setStatus(OrderStatus.FAILED);
        orderRepository.save(order);
        log.error("Order {} permanently marked as FAILED after max retries", orderId);
    }

    @CircuitBreaker(name = "paymentGateway")
    @Transactional
    public void processPayment(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found: " + orderId));

        boolean success = random.nextInt(10) != 0; // 90% success rate

        if (success) {
            order.setStatus(OrderStatus.PAID);
            log.info("Payment succeeded for order {}", orderId);
        } else {
            order.setStatus(OrderStatus.FAILED);
            log.warn("Payment failed for order {}", orderId);
            throw new PaymentGatewayException("Payment gateway error");
        }
        orderRepository.save(order);
    }
}