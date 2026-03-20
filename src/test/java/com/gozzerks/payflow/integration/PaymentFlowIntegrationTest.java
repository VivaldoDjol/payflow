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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Integration tests for the complete payment flow.
 * Tests the full stack: HTTP → Controller → Service → Database → RabbitMQ → Payment Processing
 * Uses Testcontainers for PostgreSQL and RabbitMQ to ensure realistic testing.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({TestcontainersConfiguration.class, TestSecurityConfig.class})
@DisplayName("Payment Flow Integration Tests")
class PaymentFlowIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private OrderRepository orderRepository;

    @Nested
    @DisplayName("Complete Payment Flow Tests")
    class PaymentFlowTests {

        @Test
        @DisplayName("Should process payment end-to-end and transition to PAID or FAILED")
        void shouldProcessPaymentEndToEnd() {
            // Arrange
            String idempotencyKey = "test-flow-" + UUID.randomUUID();
            CreateOrderRequest request = new CreateOrderRequest(new BigDecimal("19.99"), "GBP");

            // Act
            Long orderId = createOrderAndGetId(request, idempotencyKey);

            // Assert - Wait for async payment processing (max 10s)
            await().atMost(10, SECONDS).untilAsserted(() -> {
                OrderResponse order = getOrderById(orderId);
                assertThat(order.status()).isIn("PAID", "FAILED");
                assertThat(order.amount()).isEqualByComparingTo("19.99");
                assertThat(order.currency()).isEqualTo("GBP");
            });

            // Verify database persistence
            Optional<Order> dbOrder = orderRepository.findById(orderId);
            assertThat(dbOrder).isPresent();
            assertThat(dbOrder.get().getIdempotencyKey()).isEqualTo(idempotencyKey);
        }

        @Test
        @DisplayName("Should handle multiple concurrent orders independently")
        void shouldHandleMultipleConcurrentOrders() {
            // Arrange
            String key1 = "concurrent-1-" + UUID.randomUUID();
            String key2 = "concurrent-2-" + UUID.randomUUID();
            CreateOrderRequest request1 = new CreateOrderRequest(new BigDecimal("25.00"), "USD");
            CreateOrderRequest request2 = new CreateOrderRequest(new BigDecimal("50.00"), "EUR");

            // Act
            Long orderId1 = createOrderAndGetId(request1, key1);
            Long orderId2 = createOrderAndGetId(request2, key2);

            // Assert
            await().atMost(10, SECONDS).untilAsserted(() -> {
                OrderResponse order1 = getOrderById(orderId1);
                OrderResponse order2 = getOrderById(orderId2);

                assertThat(order1.status()).isIn("PAID", "FAILED");
                assertThat(order2.status()).isIn("PAID", "FAILED");
                assertThat(order1.id()).isNotEqualTo(order2.id());
            });
        }
    }

    @Nested
    @DisplayName("Idempotency Tests")
    class IdempotencyTests {

        @Test
        @DisplayName("Should return same order for duplicate idempotency key")
        void shouldReturnSameOrderForDuplicateKey() {
            // Arrange
            String idempotencyKey = "duplicate-test-" + UUID.randomUUID();
            CreateOrderRequest request = new CreateOrderRequest(new BigDecimal("30.00"), "GBP");

            // Act - Create same order twice
            ResponseEntity<OrderResponse> firstResponse = createOrder(request, idempotencyKey);
            ResponseEntity<OrderResponse> secondResponse = createOrder(request, idempotencyKey);

            // Assert
            assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(secondResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(firstResponse.getBody()).isNotNull();
            assertThat(secondResponse.getBody()).isNotNull();
            assertThat(firstResponse.getBody().id()).isEqualTo(secondResponse.getBody().id());
            assertThat(firstResponse.getBody().idempotencyKey()).isEqualTo(idempotencyKey);
            assertThat(secondResponse.getBody().idempotencyKey()).isEqualTo(idempotencyKey);
        }

        @Test
        @DisplayName("Should prevent duplicate order even with different request body")
        void shouldPreventDuplicateOrderWithDifferentBody() {
            // Arrange
            String idempotencyKey = "fixed-key-" + UUID.randomUUID();
            CreateOrderRequest request1 = new CreateOrderRequest(new BigDecimal("100.00"), "GBP");
            CreateOrderRequest request2 = new CreateOrderRequest(new BigDecimal("200.00"), "USD");

            // Act
            ResponseEntity<OrderResponse> firstResponse = createOrder(request1, idempotencyKey);
            ResponseEntity<OrderResponse> secondResponse = createOrder(request2, idempotencyKey);

            // Assert - Should return same order ignoring second request
            assertThat(firstResponse.getBody()).isNotNull();
            assertThat(secondResponse.getBody()).isNotNull();
            assertThat(firstResponse.getBody().id()).isEqualTo(secondResponse.getBody().id());
            assertThat(secondResponse.getBody().amount()).isEqualByComparingTo("100.00");
            assertThat(secondResponse.getBody().currency()).isEqualTo("GBP");
        }

        @Test
        @DisplayName("Should generate idempotency key when not provided")
        void shouldGenerateIdempotencyKeyWhenNotProvided() {
            // Arrange
            CreateOrderRequest request = new CreateOrderRequest(new BigDecimal("15.99"), "EUR");

            // Act
            ResponseEntity<OrderResponse> response = createOrder(request, null);

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().idempotencyKey()).isNotNull();
            assertThat(response.getBody().idempotencyKey()).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("Validation Tests")
    class ValidationTests {

        @Test
        @DisplayName("Should reject null amount")
        void shouldRejectNullAmount() {
            // Arrange
            CreateOrderRequest request = new CreateOrderRequest(null, "GBP");
            String idempotencyKey = "null-amount-" + UUID.randomUUID();

            // Act
            ResponseEntity<OrderResponse> response = createOrder(request, idempotencyKey);

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("Should reject zero amount")
        void shouldRejectZeroAmount() {
            // Arrange
            CreateOrderRequest request = new CreateOrderRequest(BigDecimal.ZERO, "GBP");
            String idempotencyKey = "zero-amount-" + UUID.randomUUID();

            // Act
            ResponseEntity<OrderResponse> response = createOrder(request, idempotencyKey);

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("Should reject negative amount")
        void shouldRejectNegativeAmount() {
            // Arrange
            CreateOrderRequest request = new CreateOrderRequest(new BigDecimal("-10.00"), "GBP");
            String idempotencyKey = "negative-amount-" + UUID.randomUUID();

            // Act
            ResponseEntity<OrderResponse> response = createOrder(request, idempotencyKey);

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("Should reject null currency")
        void shouldRejectNullCurrency() {
            // Arrange
            CreateOrderRequest request = new CreateOrderRequest(new BigDecimal("29.99"), null);
            String idempotencyKey = "null-currency-" + UUID.randomUUID();

            // Act
            ResponseEntity<OrderResponse> response = createOrder(request, idempotencyKey);

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("Should reject empty currency")
        void shouldRejectEmptyCurrency() {
            // Arrange
            CreateOrderRequest request = new CreateOrderRequest(new BigDecimal("29.99"), "");
            String idempotencyKey = "empty-currency-" + UUID.randomUUID();

            // Act
            ResponseEntity<OrderResponse> response = createOrder(request, idempotencyKey);

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("Should reject invalid currency format")
        void shouldRejectInvalidCurrencyFormat() {
            // Arrange
            CreateOrderRequest request = new CreateOrderRequest(new BigDecimal("29.99"), "gbp");
            String idempotencyKey = "invalid-currency-" + UUID.randomUUID();

            // Act
            ResponseEntity<OrderResponse> response = createOrder(request, idempotencyKey);

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @Nested
    @DisplayName("Database Persistence Tests")
    class PersistenceTests {

        @Test
        @DisplayName("Should persist order in database")
        void shouldPersistOrderInDatabase() {
            // Arrange
            String idempotencyKey = "persistence-test-" + UUID.randomUUID();
            CreateOrderRequest request = new CreateOrderRequest(new BigDecimal("45.50"), "USD");

            // Act
            Long orderId = createOrderAndGetId(request, idempotencyKey);

            // Assert
            Optional<Order> savedOrder = orderRepository.findById(orderId);
            assertThat(savedOrder).isPresent();
            assertThat(savedOrder.get().getAmount()).isEqualByComparingTo("45.50");
            assertThat(savedOrder.get().getCurrency()).isEqualTo("USD");
            assertThat(savedOrder.get().getIdempotencyKey()).isEqualTo(idempotencyKey);
        }

        @Test
        @DisplayName("Should find order by idempotency key")
        void shouldFindOrderByIdempotencyKey() {
            // Arrange
            String idempotencyKey = "find-by-key-" + UUID.randomUUID();
            CreateOrderRequest request = new CreateOrderRequest(new BigDecimal("60.00"), "EUR");

            // Act
            createOrderAndGetId(request, idempotencyKey);

            // Assert
            Optional<Order> foundOrder = orderRepository.findByIdempotencyKey(idempotencyKey);
            assertThat(foundOrder).isPresent();
            assertThat(foundOrder.get().getIdempotencyKey()).isEqualTo(idempotencyKey);
            assertThat(foundOrder.get().getAmount()).isEqualByComparingTo("60.00");
        }
    }

    @Nested
    @DisplayName("GET Order Tests")
    class GetOrderTests {

        @Test
        @DisplayName("Should retrieve order by ID")
        void shouldRetrieveOrderById() {
            // Arrange
            String idempotencyKey = "get-test-" + UUID.randomUUID();
            CreateOrderRequest request = new CreateOrderRequest(new BigDecimal("35.00"), "GBP");
            Long orderId = createOrderAndGetId(request, idempotencyKey);

            // Act
            OrderResponse response = getOrderById(orderId);

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.id()).isEqualTo(orderId);
            assertThat(response.amount()).isEqualByComparingTo("35.00");
            assertThat(response.currency()).isEqualTo("GBP");
        }

        @Test
        @DisplayName("Should return 404 for non-existent order")
        void shouldReturn404ForNonExistentOrder() {
            // Act
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(TestJwtTokenFactory.generateToken("orders:read"));
            ResponseEntity<OrderResponse> response = restTemplate.exchange(
                    "http://localhost:" + port + "/orders/999999",
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    OrderResponse.class
            );

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
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