package com.gozzerks.payflow.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gozzerks.payflow.dto.CreateOrderRequest;
import com.gozzerks.payflow.dto.OrderResponse;
import com.gozzerks.payflow.exception.OrderNotFoundException;
import com.gozzerks.payflow.service.OrderService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OrderController.class)
@DisplayName("OrderController Tests")
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private OrderService orderService;

    @Nested
    @DisplayName("POST /orders - Create Order")
    class CreateOrderTests {

        @Test
        @DisplayName("Should create order successfully with valid request")
        void shouldCreateOrderSuccessfully() throws Exception {
            // Arrange
            CreateOrderRequest request = new CreateOrderRequest(new BigDecimal("29.99"), "GBP");
            OrderResponse response = new OrderResponse(
                    1L,
                    new BigDecimal("29.99"),
                    "GBP",
                    "PROCESSING",
                    "test-key-123",
                    LocalDateTime.now()
            );

            when(orderService.createOrder(any(CreateOrderRequest.class), eq("test-key-123")))
                    .thenReturn(response);

            // Act & Assert
            mockMvc.perform(post("/orders")
                            .header("Idempotency-Key", "test-key-123")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.amount").value(29.99))
                    .andExpect(jsonPath("$.currency").value("GBP"))
                    .andExpect(jsonPath("$.status").value("PROCESSING"))
                    .andExpect(jsonPath("$.idempotencyKey").value("test-key-123"));
        }

        @Test
        @DisplayName("Should return 400 when amount is null")
        void shouldReturn400WhenAmountIsNull() throws Exception {
            // Arrange
            String invalidJson = "{\"currency\":\"GBP\"}";

            // Act & Assert
            mockMvc.perform(post("/orders")
                            .header("Idempotency-Key", "test-key-456")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(invalidJson))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should return 400 when amount is zero")
        void shouldReturn400WhenAmountIsZero() throws Exception {
            // Arrange
            CreateOrderRequest request = new CreateOrderRequest(BigDecimal.ZERO, "GBP");

            when(orderService.createOrder(any(CreateOrderRequest.class), any(String.class)))
                    .thenThrow(new IllegalArgumentException("Amount must be greater than zero"));

            // Act & Assert
            mockMvc.perform(post("/orders")
                            .header("Idempotency-Key", "test-key-789")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should return 400 when currency is missing")
        void shouldReturn400WhenCurrencyIsMissing() throws Exception {
            // Arrange
            String invalidJson = "{\"amount\":29.99}";

            // Act & Assert
            mockMvc.perform(post("/orders")
                            .header("Idempotency-Key", "test-key-999")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(invalidJson))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should handle missing Idempotency-Key header")
        void shouldHandleMissingIdempotencyKey() throws Exception {
            // Arrange
            CreateOrderRequest request = new CreateOrderRequest(new BigDecimal("29.99"), "GBP");
            OrderResponse response = new OrderResponse(
                    1L,
                    new BigDecimal("29.99"),
                    "GBP",
                    "PROCESSING",
                    null,
                    LocalDateTime.now()
            );

            when(orderService.createOrder(any(CreateOrderRequest.class), eq(null)))
                    .thenReturn(response);

            // Act & Assert
            mockMvc.perform(post("/orders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());
        }
    }

    @Nested
    @DisplayName("GET /orders/{id} - Get Order by ID")
    class GetOrderTests {

        @Test
        @DisplayName("Should return order when ID exists")
        void shouldReturnOrderWhenIdExists() throws Exception {
            // Arrange
            Long orderId = 1L;
            OrderResponse response = new OrderResponse(
                    orderId,
                    new BigDecimal("29.99"),
                    "GBP",
                    "PROCESSING",
                    "test-key-123",
                    LocalDateTime.now()
            );

            when(orderService.getOrderById(orderId)).thenReturn(response);

            // Act & Assert
            mockMvc.perform(get("/orders/{id}", orderId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(orderId))
                    .andExpect(jsonPath("$.amount").value(29.99))
                    .andExpect(jsonPath("$.currency").value("GBP"))
                    .andExpect(jsonPath("$.status").value("PROCESSING"));
        }

        @Test
        @DisplayName("Should return 404 when order not found")
        void shouldReturn404WhenOrderNotFound() throws Exception {
            // Arrange
            Long orderId = 999L;
            when(orderService.getOrderById(orderId))
                    .thenThrow(new OrderNotFoundException("Order not found with ID: " + orderId));

            // Act & Assert
            mockMvc.perform(get("/orders/{id}", orderId))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Should return 400 when ID is invalid")
        void shouldReturn400WhenIdIsInvalid() throws Exception {
            // Act & Assert
            mockMvc.perform(get("/orders/{id}", "invalid"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("GlobalExceptionHandler - additional cases")
    class GlobalExceptionHandlerTests {

        @Test
        @DisplayName("Should return 400 with 'Invalid Request Body' when JSON is malformed")
        void shouldReturn400ForMalformedJson() throws Exception {
            // Arrange
            String malformedJson = "{";

            // Act & Assert
            mockMvc.perform(post("/orders")
                            .header("Idempotency-Key", "test-key-123")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(malformedJson))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title").value("Invalid Request Body"));
        }

        @Test
        @DisplayName("Should return 405 when HTTP method is not supported")
        void shouldReturn405ForUnsupportedMethod() throws Exception {
            // Act & Assert
            mockMvc.perform(delete("/orders/1"))
                    .andExpect(status().isMethodNotAllowed())
                    .andExpect(jsonPath("$.title").value("Method Not Allowed"));
        }

        @Test
        @DisplayName("Should return 400 when service throws IllegalArgumentException")
        void shouldReturn400WhenServiceThrowsIllegalArgumentException() throws Exception {
            // Arrange
            CreateOrderRequest request = new CreateOrderRequest(new BigDecimal("29.99"), "GBP");
            when(orderService.createOrder(any(CreateOrderRequest.class), any()))
                    .thenThrow(new IllegalArgumentException("Amount must be greater than zero"));

            // Act & Assert
            mockMvc.perform(post("/orders")
                            .header("Idempotency-Key", "test-key-123")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title").value("Invalid Request"));
        }

        @Test
        @DisplayName("Should return 500 when service throws unexpected exception")
        void shouldReturn500ForUnexpectedException() throws Exception {
            // Arrange
            CreateOrderRequest request = new CreateOrderRequest(new BigDecimal("29.99"), "GBP");
            when(orderService.createOrder(any(CreateOrderRequest.class), any()))
                    .thenThrow(new RuntimeException("Unexpected error"));

            // Act & Assert
            mockMvc.perform(post("/orders")
                            .header("Idempotency-Key", "test-key-123")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.title").value("Internal Server Error"));
        }
    }
}