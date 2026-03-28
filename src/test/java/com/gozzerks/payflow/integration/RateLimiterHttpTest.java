package com.gozzerks.payflow.integration;

import com.gozzerks.payflow.config.TestJwtTokenFactory;
import com.gozzerks.payflow.config.TestSecurityConfig;
import com.gozzerks.payflow.config.TestcontainersConfiguration;
import com.gozzerks.payflow.dto.CreateOrderRequest;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.*;
import org.springframework.test.annotation.DirtiesContext;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests HTTP 429 behavior when the rate limiter rejects a request.
 * <p>
 * Runs in its own Spring context with a long refresh period (60s) to guarantee
 * that drained permits are not replenished during test execution — even on slow
 * CI runners. {@code @DirtiesContext} destroys this isolated context afterward,
 * preventing interference with other integration tests that share the default context.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "resilience4j.ratelimiter.instances.createOrder.limitRefreshPeriod=60s"
)
@Import({TestcontainersConfiguration.class, TestSecurityConfig.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("Rate Limiter HTTP Tests")
class RateLimiterHttpTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private RateLimiterRegistry rateLimiterRegistry;

    @Test
    @DisplayName("Should return 429 when rate limit is exhausted")
    void shouldReturn429WhenRateLimitExhausted() {
        // Drain all available permits so the next request is rejected
        RateLimiter rateLimiter = rateLimiterRegistry.rateLimiter("createOrder");
        rateLimiter.drainPermissions();

        // Build an authenticated POST /orders request
        String url = "http://localhost:" + port + "/orders";
        CreateOrderRequest request = new CreateOrderRequest(new BigDecimal("10.00"), "GBP");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", "rate-limit-test-key");
        headers.setBearerAuth(TestJwtTokenFactory.generateToken("orders:write"));
        HttpEntity<CreateOrderRequest> entity = new HttpEntity<>(request, headers);

        // Act
        ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);

        // Assert - should be rejected with 429 Too Many Requests
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }
}
