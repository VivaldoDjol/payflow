package com.gozzerks.payflow.integration;

import com.gozzerks.payflow.config.TestSecurityConfig;
import com.gozzerks.payflow.config.TestcontainersConfiguration;
import com.gozzerks.payflow.exception.PaymentGatewayException;
import com.gozzerks.payflow.model.Order;
import com.gozzerks.payflow.model.OrderStatus;
import com.gozzerks.payflow.repository.OrderRepository;
import com.gozzerks.payflow.service.PaymentService;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;


@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "payment.failure.rate=1",
                "resilience4j.retry.instances.paymentGateway.waitDuration=1ms"
        }
)
@Import({TestcontainersConfiguration.class, TestSecurityConfig.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("Circuit Breaker Behaviour Tests")
class CircuitBreakerBehaviourTest {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @Test
    @DisplayName("Should trip to OPEN after sustained payment failures")
    void shouldTripToOpenAfterSustainedFailures() {
        // Arrange
        Order order = new Order(new BigDecimal("10.00"), "GBP", "cb-trip-" + UUID.randomUUID());
        order.setStatus(OrderStatus.PROCESSING);
        Long orderId = orderRepository.save(order).getId();

        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("paymentGateway");
        cb.reset();
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);

        // Act
        // Every payment fails (failure.rate=1). Each processPayment call records one failure with
        // the breaker (retry is the inner aspect), so after 5 calls at 100% failure it must open.
        for (int i = 0; i < 12 && cb.getState() == CircuitBreaker.State.CLOSED; i++) {
            try {
                paymentService.processPayment(orderId);
            } catch (PaymentGatewayException | CallNotPermittedException expected) {
                // PaymentGatewayException = a recorded failure; CallNotPermitted = already open
            }
        }

        // Assert
        assertThat(cb.getState())
                .as("circuit breaker should open after exceeding the 50%% failure threshold")
                .isEqualTo(CircuitBreaker.State.OPEN);

        // And once open, further calls are rejected without invoking the payment logic
        assertThat(cb.tryAcquirePermission())
                .as("an OPEN breaker should not permit further calls")
                .isFalse();
    }
}