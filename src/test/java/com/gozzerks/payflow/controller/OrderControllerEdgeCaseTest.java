package com.gozzerks.payflow.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gozzerks.payflow.config.SecurityConfig;
import com.gozzerks.payflow.dto.CreateOrderRequest;
import com.gozzerks.payflow.dto.OrderResponse;
import com.gozzerks.payflow.service.OrderService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OrderController.class)
@Import(SecurityConfig.class)
@DisplayName("OrderController — Edge Cases")
class OrderControllerEdgeCaseTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private OrderService orderService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private static final OrderResponse SAMPLE_RESPONSE = new OrderResponse(
            1L, new BigDecimal("29.99"), "GBP", "PROCESSING", "test-key", Instant.now()
    );

    @Nested
    @DisplayName("Content-Type edge cases")
    class ContentTypeEdgeCases {

        @Test
        @DisplayName("Should return 415 when content type is text/plain")
        void shouldReturn415ForTextPlain() throws Exception {
            // Act & Assert
            mockMvc.perform(post("/orders")
                            .with(jwt().authorities(() -> "SCOPE_orders:write"))
                            .header("Idempotency-Key", "ct-test")
                            .contentType(MediaType.TEXT_PLAIN)
                            .content("{\"amount\":29.99,\"currency\":\"GBP\"}"))
                    .andExpect(status().isUnsupportedMediaType())
                    .andExpect(jsonPath("$.title").value("Unsupported Media Type"));
        }

        @Test
        @DisplayName("Should return 415 when content type is application/xml")
        void shouldReturn415ForXml() throws Exception {
            // Act & Assert
            mockMvc.perform(post("/orders")
                            .with(jwt().authorities(() -> "SCOPE_orders:write"))
                            .header("Idempotency-Key", "xml-test")
                            .contentType(MediaType.APPLICATION_XML)
                            .content("<order><amount>29.99</amount><currency>GBP</currency></order>"))
                    .andExpect(status().isUnsupportedMediaType())
                    .andExpect(jsonPath("$.title").value("Unsupported Media Type"));
        }

        @Test
        @DisplayName("Should return 400 when body is completely empty")
        void shouldReturn400ForEmptyBody() throws Exception {
            // Act & Assert
            mockMvc.perform(post("/orders")
                            .with(jwt().authorities(() -> "SCOPE_orders:write"))
                            .header("Idempotency-Key", "empty-body")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(""))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should return 400 when body is empty JSON object")
        void shouldReturn400ForEmptyJsonObject() throws Exception {
            // Act & Assert
            mockMvc.perform(post("/orders")
                            .with(jwt().authorities(() -> "SCOPE_orders:write"))
                            .header("Idempotency-Key", "empty-json")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("Request body edge cases")
    class RequestBodyEdgeCases {

        @Test
        @DisplayName("Should return 400 for amount exceeding maximum via DTO validation")
        void shouldReturn400ForAmountExceedingMax() throws Exception {
            // Arrange
            String json = "{\"amount\":177778.0000,\"currency\":\"GBP\"}";

            // Act & Assert
            mockMvc.perform(post("/orders")
                            .with(jwt().authorities(() -> "SCOPE_orders:write"))
                            .header("Idempotency-Key", "over-max")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should return 400 for lowercase currency via DTO pattern validation")
        void shouldReturn400ForLowercaseCurrency() throws Exception {
            // Arrange
            String json = "{\"amount\":29.99,\"currency\":\"gbp\"}";

            // Act & Assert
            mockMvc.perform(post("/orders")
                            .with(jwt().authorities(() -> "SCOPE_orders:write"))
                            .header("Idempotency-Key", "lower-curr")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should return 400 for 2-letter currency code")
        void shouldReturn400ForTwoLetterCurrency() throws Exception {
            // Arrange
            String json = "{\"amount\":29.99,\"currency\":\"GB\"}";

            // Act & Assert
            mockMvc.perform(post("/orders")
                            .with(jwt().authorities(() -> "SCOPE_orders:write"))
                            .header("Idempotency-Key", "short-curr")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should return 400 for 4-letter currency code")
        void shouldReturn400ForFourLetterCurrency() throws Exception {
            // Arrange
            String json = "{\"amount\":29.99,\"currency\":\"GBPP\"}";

            // Act & Assert
            mockMvc.perform(post("/orders")
                            .with(jwt().authorities(() -> "SCOPE_orders:write"))
                            .header("Idempotency-Key", "long-curr")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should return 400 for numeric currency")
        void shouldReturn400ForNumericCurrency() throws Exception {
            // Arrange
            String json = "{\"amount\":29.99,\"currency\":\"123\"}";

            // Act & Assert
            mockMvc.perform(post("/orders")
                            .with(jwt().authorities(() -> "SCOPE_orders:write"))
                            .header("Idempotency-Key", "num-curr")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should return 400 with multiple validation errors for both invalid fields")
        void shouldReturn400WithMultipleValidationErrors() throws Exception {
            // Arrange
            String json = "{\"amount\":null,\"currency\":\"bad\"}";

            // Act & Assert
            mockMvc.perform(post("/orders")
                            .with(jwt().authorities(() -> "SCOPE_orders:write"))
                            .header("Idempotency-Key", "multi-err")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title").value("Invalid Request"));
        }

        @Test
        @DisplayName("Should return 400 for amount as string in JSON")
        void shouldReturn400ForAmountAsString() throws Exception {
            // Arrange
            String json = "{\"amount\":\"not-a-number\",\"currency\":\"GBP\"}";

            // Act & Assert
            mockMvc.perform(post("/orders")
                            .with(jwt().authorities(() -> "SCOPE_orders:write"))
                            .header("Idempotency-Key", "str-amount")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should ignore extra unknown fields in JSON body")
        void shouldIgnoreExtraJsonFields() throws Exception {
            // Arrange
            String json = "{\"amount\":29.99,\"currency\":\"GBP\",\"unknown\":\"field\",\"hack\":true}";
            when(orderService.createOrder(any(CreateOrderRequest.class), anyString()))
                    .thenReturn(SAMPLE_RESPONSE);

            // Act & Assert
            mockMvc.perform(post("/orders")
                            .with(jwt().authorities(() -> "SCOPE_orders:write"))
                            .header("Idempotency-Key", "extra-fields")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("Should return 400 for negative amount via DTO validation")
        void shouldReturn400ForNegativeAmountViaDto() throws Exception {
            // Arrange
            String json = "{\"amount\":-5.00,\"currency\":\"GBP\"}";

            // Act & Assert
            mockMvc.perform(post("/orders")
                            .with(jwt().authorities(() -> "SCOPE_orders:write"))
                            .header("Idempotency-Key", "neg-dto")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("Malformed JSON varieties")
    class MalformedJson {

        @Test
        @DisplayName("Should return 400 for truncated JSON")
        void shouldReturn400ForTruncatedJson() throws Exception {
            // Act & Assert
            mockMvc.perform(post("/orders")
                            .with(jwt().authorities(() -> "SCOPE_orders:write"))
                            .header("Idempotency-Key", "trunc")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"amount\":29.99,\"curren"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title").value("Invalid Request Body"));
        }

        @Test
        @DisplayName("Should return 400 for JSON array instead of object")
        void shouldReturn400ForJsonArray() throws Exception {
            // Act & Assert
            mockMvc.perform(post("/orders")
                            .with(jwt().authorities(() -> "SCOPE_orders:write"))
                            .header("Idempotency-Key", "array")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("[{\"amount\":29.99,\"currency\":\"GBP\"}]"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should return 400 for plain text as JSON")
        void shouldReturn400ForPlainText() throws Exception {
            // Act & Assert
            mockMvc.perform(post("/orders")
                            .with(jwt().authorities(() -> "SCOPE_orders:write"))
                            .header("Idempotency-Key", "plain")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("this is not json"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("Path variable edge cases for GET /orders/{id}")
    class PathVariableEdgeCases {

        @Test
        @DisplayName("Should pass zero id through to the service and return 200")
        void shouldHandleZeroId() throws Exception {
            // Arrange - zero is a valid Long; the controller passes it through, the service decides
            when(orderService.getOrderById(0L)).thenReturn(SAMPLE_RESPONSE);

            // Act & Assert
            mockMvc.perform(get("/orders/0")
                            .with(jwt().authorities(() -> "SCOPE_orders:read")))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Should pass negative id through to the service and return 200")
        void shouldHandleNegativeId() throws Exception {
            // Arrange - -1 is a valid Long; the controller passes it through, the service decides
            when(orderService.getOrderById(-1L)).thenReturn(SAMPLE_RESPONSE);

            // Act & Assert
            mockMvc.perform(get("/orders/-1")
                            .with(jwt().authorities(() -> "SCOPE_orders:read")))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Should return 400 for GET /orders/abc (non-numeric)")
        void shouldReturn400ForNonNumericId() throws Exception {
            // Act & Assert
            mockMvc.perform(get("/orders/abc")
                            .with(jwt().authorities(() -> "SCOPE_orders:read")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title").value("Type Mismatch"));
        }

        @Test
        @DisplayName("Should return 400 for GET /orders/1.5 (decimal)")
        void shouldReturn400ForDecimalId() throws Exception {
            // Act & Assert
            mockMvc.perform(get("/orders/1.5")
                            .with(jwt().authorities(() -> "SCOPE_orders:read")))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should return 400 for GET /orders/9999999999999999999 (overflow)")
        void shouldReturn400ForOverflowId() throws Exception {
            // Act & Assert
            mockMvc.perform(get("/orders/9999999999999999999")
                            .with(jwt().authorities(() -> "SCOPE_orders:read")))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should handle Long.MAX_VALUE as path variable")
        void shouldHandleLongMaxValue() throws Exception {
            // Arrange
            when(orderService.getOrderById(Long.MAX_VALUE)).thenReturn(SAMPLE_RESPONSE);

            // Act & Assert
            mockMvc.perform(get("/orders/" + Long.MAX_VALUE)
                            .with(jwt().authorities(() -> "SCOPE_orders:read")))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("Idempotency-Key header edge cases at controller level")
    class HeaderEdgeCases {

        @Test
        @DisplayName("Whitespace-only header should generate UUID in controller")
        void whitespaceHeaderShouldGenerateUuid() throws Exception {
            // Arrange
            CreateOrderRequest request = new CreateOrderRequest(new BigDecimal("29.99"), "GBP");
            when(orderService.createOrder(any(CreateOrderRequest.class), anyString()))
                    .thenReturn(SAMPLE_RESPONSE);

            // Act & Assert
            mockMvc.perform(post("/orders")
                            .with(jwt().authorities(() -> "SCOPE_orders:write"))
                            .header("Idempotency-Key", "   ")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("Tab-only header should generate UUID in controller")
        void tabHeaderShouldGenerateUuid() throws Exception {
            // Arrange
            CreateOrderRequest request = new CreateOrderRequest(new BigDecimal("29.99"), "GBP");
            when(orderService.createOrder(any(CreateOrderRequest.class), anyString()))
                    .thenReturn(SAMPLE_RESPONSE);

            // Act & Assert
            mockMvc.perform(post("/orders")
                            .with(jwt().authorities(() -> "SCOPE_orders:write"))
                            .header("Idempotency-Key", "\t")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());
        }
    }

    @Nested
    @DisplayName("Security — additional edge cases")
    class SecurityEdgeCases {

        @Test
        @DisplayName("JWT with both read and write scopes should allow POST")
        void bothScopesShouldAllowPost() throws Exception {
            // Arrange
            CreateOrderRequest request = new CreateOrderRequest(new BigDecimal("29.99"), "GBP");
            when(orderService.createOrder(any(CreateOrderRequest.class), anyString()))
                    .thenReturn(SAMPLE_RESPONSE);

            // Act & Assert
            mockMvc.perform(post("/orders")
                            .with(jwt().authorities(() -> "SCOPE_orders:write", () -> "SCOPE_orders:read"))
                            .header("Idempotency-Key", "both-scopes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("JWT with both read and write scopes should allow GET")
        void bothScopesShouldAllowGet() throws Exception {
            // Arrange
            when(orderService.getOrderById(1L)).thenReturn(SAMPLE_RESPONSE);

            // Act & Assert
            mockMvc.perform(get("/orders/1")
                            .with(jwt().authorities(() -> "SCOPE_orders:write", () -> "SCOPE_orders:read")))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("JWT with empty scope should return 403 for POST")
        void emptyScopeShouldReturn403ForPost() throws Exception {
            // Arrange
            CreateOrderRequest request = new CreateOrderRequest(new BigDecimal("29.99"), "GBP");

            // Act & Assert
            mockMvc.perform(post("/orders")
                            .with(jwt().authorities(() -> ""))
                            .header("Idempotency-Key", "empty-scope")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("JWT with unrelated scope should return 403 for POST")
        void unrelatedScopeShouldReturn403() throws Exception {
            // Arrange
            CreateOrderRequest request = new CreateOrderRequest(new BigDecimal("29.99"), "GBP");

            // Act & Assert
            mockMvc.perform(post("/orders")
                            .with(jwt().authorities(() -> "SCOPE_admin:all"))
                            .header("Idempotency-Key", "unrelated-scope")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("PUT /orders should require authentication")
        void putShouldRequireAuth() throws Exception {
            // Act & Assert
            mockMvc.perform(put("/orders/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"amount\":29.99,\"currency\":\"GBP\"}"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("PATCH /orders should require authentication")
        void patchShouldRequireAuth() throws Exception {
            // Act & Assert
            mockMvc.perform(patch("/orders/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\":\"PAID\"}"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("HTTP method not supported — additional cases")
    class UnsupportedMethods {

        @Test
        @DisplayName("PUT /orders/1 with auth should return 405")
        void putWithAuthShouldReturn405() throws Exception {
            // Act & Assert
            mockMvc.perform(put("/orders/1")
                            .with(jwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"amount\":29.99,\"currency\":\"GBP\"}"))
                    .andExpect(status().isMethodNotAllowed());
        }

        @Test
        @DisplayName("PATCH /orders/1 with auth should return 405")
        void patchWithAuthShouldReturn405() throws Exception {
            // Act & Assert
            mockMvc.perform(patch("/orders/1")
                            .with(jwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\":\"PAID\"}"))
                    .andExpect(status().isMethodNotAllowed());
        }
    }
}