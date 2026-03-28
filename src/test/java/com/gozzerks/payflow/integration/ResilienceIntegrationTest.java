package com.gozzerks.payflow.integration;

import com.gozzerks.payflow.config.TestSecurityConfig;
import com.gozzerks.payflow.config.TestcontainersConfiguration;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({TestcontainersConfiguration.class, TestSecurityConfig.class})
@DisplayName("Resilience Integration Tests")
class ResilienceIntegrationTest {

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @Autowired
    private RetryRegistry retryRegistry;

    @Autowired
    private RateLimiterRegistry rateLimiterRegistry;

    @Nested
    @DisplayName("Circuit Breaker Configuration")
    class CircuitBreakerConfigTests {

        @Test
        @DisplayName("Should load paymentGateway circuit breaker with correct config")
        void shouldLoadCircuitBreakerConfig() {
            CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("paymentGateway");

            assertThat(cb).isNotNull();
            assertThat(cb.getCircuitBreakerConfig().getSlidingWindowSize()).isEqualTo(10);
            assertThat(cb.getCircuitBreakerConfig().getMinimumNumberOfCalls()).isEqualTo(5);
            assertThat(cb.getCircuitBreakerConfig().getFailureRateThreshold()).isEqualTo(50f);
            assertThat(cb.getCircuitBreakerConfig().getPermittedNumberOfCallsInHalfOpenState())
                    .isEqualTo(3);
            assertThat(cb.getCircuitBreakerConfig().isAutomaticTransitionFromOpenToHalfOpenEnabled())
                    .isTrue();
        }

        @Test
        @DisplayName("Should start in CLOSED state")
        void shouldStartInClosedState() {
            CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("paymentGateway");

            assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        }
    }

    @Nested
    @DisplayName("Retry Configuration")
    class RetryConfigTests {

        @Test
        @DisplayName("Should load paymentGateway retry with correct config")
        void shouldLoadRetryConfig() {
            var retry = retryRegistry.retry("paymentGateway");

            assertThat(retry).isNotNull();
            assertThat(retry.getRetryConfig().getMaxAttempts()).isEqualTo(3);
            assertThat(retry.getRetryConfig().getIntervalBiFunction().apply(1, null))
                    .isEqualTo(500L);
        }
    }

    @Nested
    @DisplayName("Rate Limiter Configuration")
    class RateLimiterConfigTests {

        @Test
        @DisplayName("Should load createOrder rate limiter with correct config")
        void shouldLoadRateLimiterConfig() {
            RateLimiter rl = rateLimiterRegistry.rateLimiter("createOrder");

            assertThat(rl).isNotNull();
            assertThat(rl.getRateLimiterConfig().getLimitForPeriod()).isEqualTo(50);
            assertThat(rl.getRateLimiterConfig().getLimitRefreshPeriod())
                    .isEqualTo(Duration.ofSeconds(1));
            assertThat(rl.getRateLimiterConfig().getTimeoutDuration())
                    .isEqualTo(Duration.ZERO);
        }
    }
}
