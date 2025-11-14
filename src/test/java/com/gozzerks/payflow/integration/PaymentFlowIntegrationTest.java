package com.gozzerks.payflow.integration;

import com.gozzerks.payflow.dto.CreateOrderRequest;
import com.gozzerks.payflow.dto.OrderResponse;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static java.util.concurrent.TimeUnit.SECONDS;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PaymentFlowIntegrationTest {

    @LocalServerPort
    private int port;

    private final TestRestTemplate restTemplate = new TestRestTemplate();

    @Test
    void shouldProcessPaymentInBackgroundAndTransitionToPaidOrFailed() {
        // 1. Create order
        String key = "test-flow-" + UUID.randomUUID();
        Long orderId = createOrderAndGetId(key);

        // 2. Wait for async payment to complete (max 10s)
        await().atMost(10, SECONDS).untilAsserted(() -> {
            OrderResponse order = getOrderById(orderId);
            assertThat(order.status()).isIn("PAID", "FAILED"); // 90%/10% split
        });
    }

    private Long createOrderAndGetId(String key) {
        CreateOrderRequest request = new CreateOrderRequest(new BigDecimal("19.99"), "GBP");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", key);
        HttpEntity<CreateOrderRequest> entity = new HttpEntity<>(request, headers);

        ResponseEntity<OrderResponse> response = restTemplate.exchange(
                "http://localhost:" + port + "/orders",
                HttpMethod.POST,
                entity,
                OrderResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Assertions.assertNotNull(response.getBody());
        return response.getBody().id();
    }

    private OrderResponse getOrderById(Long id) {
        ResponseEntity<OrderResponse> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/orders/" + id,
                OrderResponse.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }
}