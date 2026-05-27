package com.gozzerks.payflow.integration;

import com.gozzerks.payflow.config.TestJwtTokenFactory;
import com.gozzerks.payflow.config.TestSecurityConfig;
import com.gozzerks.payflow.config.TestcontainersConfiguration;
import com.gozzerks.payflow.dto.CreateOrderRequest;
import com.gozzerks.payflow.dto.OrderResponse;
import com.gozzerks.payflow.model.Order;
import com.gozzerks.payflow.repository.OrderRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.*;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({TestcontainersConfiguration.class, TestSecurityConfig.class})
@DisplayName("Edge Case Integration Tests")
class EdgeCaseIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private OrderRepository orderRepository;

    @Nested
    @DisplayName("Concurrent idempotency race condition")
    class ConcurrentIdempotency {

        @Test
        @DisplayName("Concurrent requests with the same idempotency key create exactly one order")
        void concurrentSameKeyShouldNotCreateDuplicates() throws Exception {
            // Arrange
            String idempotencyKey = "race-condition-" + UUID.randomUUID();
            CreateOrderRequest request = new CreateOrderRequest(new BigDecimal("50.00"), "GBP");

            int threadCount = 10;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch startGate = new CountDownLatch(1);
            List<Future<ResponseEntity<OrderResponse>>> futures = new ArrayList<>();

            for (int i = 0; i < threadCount; i++) {
                futures.add(executor.submit(() -> {
                    startGate.await(); // All threads start simultaneously
                    return createOrder(request, idempotencyKey);
                }));
            }

            // Act - release all threads at once, then collect every response
            startGate.countDown();

            List<ResponseEntity<OrderResponse>> responses = new ArrayList<>();
            for (Future<ResponseEntity<OrderResponse>> future : futures) {
                responses.add(future.get(10, SECONDS));
            }
            executor.shutdown();

            // Assert
            // Every response must be a clean 201 - the idempotency race must NOT surface as a 500.
            // This is the assertion that was missing before: the race produced 500s while the DB
            // still ended up with one row, so a database-only check passed despite the bug.
            assertThat(responses)
                    .allSatisfy(resp -> assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED));

            // Every response must point at the SAME order id - one order, returned to all callers.
            Set<Long> orderIds = new HashSet<>();
            for (ResponseEntity<OrderResponse> resp : responses) {
                assertThat(resp.getBody()).isNotNull();
                orderIds.add(resp.getBody().id());
            }
            assertThat(orderIds).hasSize(1);

            // And the database must hold exactly one order for this key.
            List<Order> orders = orderRepository.findAll().stream()
                    .filter(o -> idempotencyKey.equals(o.getIdempotencyKey()))
                    .toList();
            assertThat(orders).hasSize(1);
        }
    }

    @Nested
    @DisplayName("Boundary amount values through full stack")
    class BoundaryAmounts {

        @Test
        @DisplayName("Should accept exact minimum amount 0.01 end-to-end")
        void shouldAcceptMinimumAmount() {
            // Arrange
            String key = "min-amount-" + UUID.randomUUID();
            CreateOrderRequest request = new CreateOrderRequest(new BigDecimal("0.01"), "GBP");

            // Act
            ResponseEntity<OrderResponse> response = createOrder(request, key);

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().amount()).isEqualByComparingTo("0.01");
        }

        @Test
        @DisplayName("Should accept exact maximum amount 177777.7777 end-to-end")
        void shouldAcceptMaximumAmount() {
            // Arrange
            String key = "max-amount-" + UUID.randomUUID();
            CreateOrderRequest request = new CreateOrderRequest(new BigDecimal("177777.7777"), "GBP");

            // Act
            ResponseEntity<OrderResponse> response = createOrder(request, key);

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().amount()).isEqualByComparingTo("177777.7777");
        }

        @Test
        @DisplayName("Should reject amount one tick above maximum (177777.7778)")
        void shouldRejectOneAboveMax() {
            // Arrange
            String key = "over-max-" + UUID.randomUUID();
            CreateOrderRequest request = new CreateOrderRequest(new BigDecimal("177777.7778"), "GBP");

            // Act
            ResponseEntity<OrderResponse> response = createOrder(request, key);

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("Should reject amount one tick below minimum (0.009)")
        void shouldRejectBelowMin() {
            // Arrange
            String key = "under-min-" + UUID.randomUUID();
            CreateOrderRequest request = new CreateOrderRequest(new BigDecimal("0.009"), "GBP");

            // Act
            ResponseEntity<OrderResponse> response = createOrder(request, key);

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @Nested
    @DisplayName("Amount rounding through full stack")
    class AmountRounding {

        @Test
        @DisplayName("Amount with 5+ decimals should be rounded to 4 places and persisted")
        void amountWithManyDecimalsShouldRound() {
            // Arrange - 29.99999 is within the DTO max; the service rounds it (HALF_UP) to 30.0000
            String key = "round-e2e-" + UUID.randomUUID();
            CreateOrderRequest request = new CreateOrderRequest(new BigDecimal("29.99999"), "GBP");

            // Act
            ResponseEntity<OrderResponse> response = createOrder(request, key);

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().amount()).isEqualByComparingTo("30.0000");

            // The persisted value is rounded too
            Optional<Order> dbOrder = orderRepository.findByIdempotencyKey(key);
            assertThat(dbOrder).isPresent();
            assertThat(dbOrder.get().getAmount()).isEqualByComparingTo("30.0000");
        }

        @Test
        @DisplayName("Whole number amount should persist with 4 decimal scale")
        void wholeNumberShouldPersistWith4Decimals() {
            // Arrange
            String key = "whole-num-" + UUID.randomUUID();
            CreateOrderRequest request = new CreateOrderRequest(new BigDecimal("100"), "USD");

            // Act
            ResponseEntity<OrderResponse> response = createOrder(request, key);

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getBody()).isNotNull();

            Optional<Order> dbOrder = orderRepository.findByIdempotencyKey(key);
            assertThat(dbOrder).isPresent();
            assertThat(dbOrder.get().getAmount().scale()).isEqualTo(4);
        }
    }

    @Nested
    @DisplayName("Security integration tests")
    class SecurityIntegration {

        @Test
        @DisplayName("Request with no Authorization header should return 401")
        void noAuthHeaderShouldReturn401() {
            // Arrange
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<CreateOrderRequest> entity = new HttpEntity<>(
                    new CreateOrderRequest(new BigDecimal("10.00"), "GBP"), headers);

            // Act
            ResponseEntity<String> response = restTemplate.exchange(
                    "http://localhost:" + port + "/orders",
                    HttpMethod.POST,
                    entity,
                    String.class
            );

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("Request with read scope should return 403 for POST")
        void readScopeOnPostShouldReturn403() {
            // Arrange
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(TestJwtTokenFactory.generateToken("orders:read"));
            HttpEntity<CreateOrderRequest> entity = new HttpEntity<>(
                    new CreateOrderRequest(new BigDecimal("10.00"), "GBP"), headers);

            // Act
            ResponseEntity<String> response = restTemplate.exchange(
                    "http://localhost:" + port + "/orders",
                    HttpMethod.POST,
                    entity,
                    String.class
            );

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        @DisplayName("Request with write scope should return 403 for GET")
        void writeScopeOnGetShouldReturn403() {
            // Arrange
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(TestJwtTokenFactory.generateToken("orders:write"));

            // Act
            ResponseEntity<String> response = restTemplate.exchange(
                    "http://localhost:" + port + "/orders/1",
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    String.class
            );

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        @DisplayName("GET / (home) should be publicly accessible without JWT")
        void homeShouldBePublic() {
            // Act
            ResponseEntity<String> response = restTemplate.getForEntity(
                    "http://localhost:" + port + "/", String.class);

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }

    @Nested
    @DisplayName("Payment processing with edge amounts")
    class PaymentProcessingEdge {

        @Test
        @DisplayName("Minimum amount order should complete payment flow")
        void minimumAmountShouldCompleteFlow() {
            // Arrange
            String key = "min-flow-" + UUID.randomUUID();
            CreateOrderRequest request = new CreateOrderRequest(new BigDecimal("0.01"), "GBP");

            // Act
            Long orderId = createOrderAndGetId(request, key);

            // Assert
            await().atMost(30, SECONDS).untilAsserted(() -> {
                OrderResponse order = getOrderById(orderId);
                assertThat(order.status()).isIn("PAID", "FAILED");
                assertThat(order.amount()).isEqualByComparingTo("0.01");
            });
        }

        @Test
        @DisplayName("Maximum amount order should complete payment flow")
        void maximumAmountShouldCompleteFlow() {
            // Arrange
            String key = "max-flow-" + UUID.randomUUID();
            CreateOrderRequest request = new CreateOrderRequest(new BigDecimal("177777.7777"), "GBP");

            // Act
            Long orderId = createOrderAndGetId(request, key);

            // Assert
            await().atMost(30, SECONDS).untilAsserted(() -> {
                OrderResponse order = getOrderById(orderId);
                assertThat(order.status()).isIn("PAID", "FAILED");
                assertThat(order.amount()).isEqualByComparingTo("177777.7777");
            });
        }
    }

    @Nested
    @DisplayName("Idempotency key edge cases — integration")
    class IdempotencyKeyIntegration {

        @Test
        @DisplayName("64-character key should work end-to-end")
        void maxLengthKeyShouldWork() {
            // Arrange
            String key = "a".repeat(64);
            CreateOrderRequest request = new CreateOrderRequest(new BigDecimal("10.00"), "GBP");

            // Act
            ResponseEntity<OrderResponse> response = createOrder(request, key);

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().idempotencyKey()).isEqualTo(key);
        }

        @Test
        @DisplayName("Key with all valid special characters (underscores and hyphens)")
        void keyWithSpecialCharsShouldWork() {
            // Arrange
            String key = "order_2024-04-06_test-123";
            CreateOrderRequest request = new CreateOrderRequest(new BigDecimal("15.00"), "EUR");

            // Act
            ResponseEntity<OrderResponse> response = createOrder(request, key);

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().idempotencyKey()).isEqualTo(key);
        }
    }

    @Nested
    @DisplayName("Invalid path handling")
    class InvalidPaths {

        @Test
        @DisplayName("GET /orders/abc should return 400")
        void nonNumericIdShouldReturn400() {
            // Arrange
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(TestJwtTokenFactory.generateToken("orders:read"));

            // Act
            ResponseEntity<String> response = restTemplate.exchange(
                    "http://localhost:" + port + "/orders/abc",
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    String.class
            );

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("DELETE /orders/1 should return 401 without JWT or 405 with JWT")
        void deleteWithoutJwtShouldReturn401() {
            // Act
            ResponseEntity<String> response = restTemplate.exchange(
                    "http://localhost:" + port + "/orders/1",
                    HttpMethod.DELETE,
                    null,
                    String.class
            );

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("POST /nonexistent should return 401 without auth")
        void postNonExistentPathWithoutAuth() {
            // Arrange
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>("{}", headers);

            // Act
            ResponseEntity<String> response = restTemplate.exchange(
                    "http://localhost:" + port + "/nonexistent",
                    HttpMethod.POST,
                    entity,
                    String.class
            );

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    // ================ Helper Methods ================

    private Long createOrderAndGetId(CreateOrderRequest request, String idempotencyKey) {
        ResponseEntity<OrderResponse> response = createOrder(request, idempotencyKey);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        return response.getBody().id();
    }

    private ResponseEntity<OrderResponse> createOrder(CreateOrderRequest request, String idempotencyKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(TestJwtTokenFactory.generateToken("orders:write"));
        if (idempotencyKey != null) {
            headers.set("Idempotency-Key", idempotencyKey);
        }
        HttpEntity<CreateOrderRequest> entity = new HttpEntity<>(request, headers);

        return restTemplate.exchange(
                "http://localhost:" + port + "/orders",
                HttpMethod.POST,
                entity,
                OrderResponse.class
        );
    }

    private OrderResponse getOrderById(Long id) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(TestJwtTokenFactory.generateToken("orders:read"));
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<OrderResponse> response = restTemplate.exchange(
                "http://localhost:" + port + "/orders/" + id,
                HttpMethod.GET,
                entity,
                OrderResponse.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        return response.getBody();
    }
}