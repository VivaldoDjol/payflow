package com.gozzerks.payflow.service;

import com.gozzerks.payflow.dto.CreateOrderRequest;
import com.gozzerks.payflow.dto.OrderResponse;
import com.gozzerks.payflow.event.PaymentRequestedEvent;
import com.gozzerks.payflow.exception.OrderNotFoundException;
import com.gozzerks.payflow.model.Order;
import com.gozzerks.payflow.model.OrderStatus;
import com.gozzerks.payflow.repository.OrderRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static com.gozzerks.payflow.config.RabbitMQConfig.PAYMENT_EXCHANGE;
import static com.gozzerks.payflow.config.RabbitMQConfig.PAYMENT_ROUTING_KEY;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderService Tests")
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private OrderService orderService;

    @Nested
    @DisplayName("Create Order Tests")
    class CreateOrderTests {

        @Test
        @DisplayName("Should create order with valid request and idempotency key")
        void shouldCreateOrderSuccessfully() {
            // Arrange
            CreateOrderRequest request = new CreateOrderRequest(new BigDecimal("29.99"), "GBP");
            String idempotencyKey = "test-key-123";

            when(orderRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
            when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
                Order order = invocation.getArgument(0);
                order.setId(1L);
                return order;
            });

            LocalDateTime before = LocalDateTime.now();

            // Act
            OrderResponse response = orderService.createOrder(request, idempotencyKey);

            LocalDateTime after = LocalDateTime.now();

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.id()).isEqualTo(1L);
            assertThat(response.amount()).isEqualByComparingTo("29.9900");
            assertThat(response.currency()).isEqualTo("GBP");
            assertThat(response.status()).isEqualTo("PROCESSING");
            assertThat(response.idempotencyKey()).isEqualTo(idempotencyKey);
            assertThat(response.createdAt()).isBetween(before, after);

            verify(orderRepository).findByIdempotencyKey(idempotencyKey);
            verify(orderRepository).save(any(Order.class));
            verify(rabbitTemplate).convertAndSend(
                eq(PAYMENT_EXCHANGE),
                eq(PAYMENT_ROUTING_KEY),
                any(PaymentRequestedEvent.class)
            );
        }

        @Test
        @DisplayName("Should return existing order for duplicate idempotency key")
        void shouldReturnExistingOrderForDuplicateKey() {
            // Arrange
            String idempotencyKey = "duplicate-key";
            Order existingOrder = new Order();
            existingOrder.setId(1L);
            existingOrder.setAmount(new BigDecimal("29.9900"));
            existingOrder.setCurrency("GBP");
            existingOrder.setStatus(OrderStatus.PROCESSING);
            existingOrder.setIdempotencyKey(idempotencyKey);
            existingOrder.setCreatedAt(LocalDateTime.now());

            when(orderRepository.findByIdempotencyKey(idempotencyKey))
                    .thenReturn(Optional.of(existingOrder));

            CreateOrderRequest request = new CreateOrderRequest(new BigDecimal("29.99"), "GBP");

            // Act
            OrderResponse response = orderService.createOrder(request, idempotencyKey);

            // Assert
            assertThat(response.id()).isEqualTo(1L);
            assertThat(response.idempotencyKey()).isEqualTo(idempotencyKey);

            verify(orderRepository).findByIdempotencyKey(idempotencyKey);
            verify(orderRepository, never()).save(any());
            verifyNoInteractions(rabbitTemplate);
        }

        @Test
        @DisplayName("Should generate UUID when idempotency key is null")
        void shouldGenerateUuidWhenKeyIsNull() {
            // Arrange
            CreateOrderRequest request = new CreateOrderRequest(new BigDecimal("29.99"), "GBP");

            when(orderRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
            when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
                Order order = invocation.getArgument(0);
                order.setId(1L);
                return order;
            });

            // Act
            OrderResponse response = orderService.createOrder(request, null);

            // Assert
            assertThat(response.idempotencyKey()).isNotNull();
            assertThat(response.idempotencyKey()).matches("[a-f0-9-]{36}");

            verify(orderRepository).save(any(Order.class));
        }

        @Test
        @DisplayName("Should generate UUID when idempotency key is blank")
        void shouldGenerateUuidWhenKeyIsBlank() {
            // Arrange
            CreateOrderRequest request = new CreateOrderRequest(new BigDecimal("29.99"), "GBP");

            when(orderRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
            when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
                Order order = invocation.getArgument(0);
                order.setId(1L);
                return order;
            });

            // Act
            OrderResponse response = orderService.createOrder(request, "   ");

            // Assert
            assertThat(response.idempotencyKey()).isNotNull();
            assertThat(response.idempotencyKey()).matches("[a-f0-9-]{36}");

            verify(orderRepository).save(any(Order.class));
        }

        @Test
        @DisplayName("Should normalize currency to uppercase")
        void shouldNormalizeCurrencyToUppercase() {
            // Arrange
            CreateOrderRequest request = new CreateOrderRequest(new BigDecimal("29.99"), "gbp");
            String idempotencyKey = "test-key";

            when(orderRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
            when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
                Order order = invocation.getArgument(0);
                order.setId(1L);
                return order;
            });

            // Act
            OrderResponse response = orderService.createOrder(request, idempotencyKey);

            // Assert
            assertThat(response.currency()).isEqualTo("GBP");

            ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
            verify(orderRepository).save(orderCaptor.capture());
            assertThat(orderCaptor.getValue().getCurrency()).isEqualTo("GBP");
        }

        @Test
        @DisplayName("Should scale amount to 4 decimal places")
        void shouldScaleAmountTo4DecimalPlaces() {
            // Arrange
            CreateOrderRequest request = new CreateOrderRequest(new BigDecimal("29.9"), "GBP");
            String idempotencyKey = "test-key";

            when(orderRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
            when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
                Order order = invocation.getArgument(0);
                order.setId(1L);
                return order;
            });

            // Act
            OrderResponse response = orderService.createOrder(request, idempotencyKey);

            // Assert
            assertThat(response.amount()).isEqualByComparingTo("29.9000");

            ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
            verify(orderRepository).save(orderCaptor.capture());
            assertThat(orderCaptor.getValue().getAmount().scale()).isEqualTo(4);
        }

        @Test
        @DisplayName("Should publish PaymentRequestedEvent to RabbitMQ")
        void shouldPublishPaymentRequestedEvent() {
            // Arrange
            CreateOrderRequest request = new CreateOrderRequest(new BigDecimal("29.99"), "GBP");
            String idempotencyKey = "test-key";

            when(orderRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
            when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
                Order order = invocation.getArgument(0);
                order.setId(1L);
                return order;
            });

            // Act
            orderService.createOrder(request, idempotencyKey);

            // Assert
            ArgumentCaptor<PaymentRequestedEvent> eventCaptor = ArgumentCaptor.forClass(PaymentRequestedEvent.class);
            verify(rabbitTemplate).convertAndSend(
                eq(PAYMENT_EXCHANGE),
                eq(PAYMENT_ROUTING_KEY),
                eventCaptor.capture()
            );

            PaymentRequestedEvent event = eventCaptor.getValue();
            assertThat(event.orderId()).isEqualTo(1L);
            assertThat(event.amount()).isEqualByComparingTo("29.9900");
            assertThat(event.currency()).isEqualTo("GBP");
            assertThat(event.idempotencyKey()).isEqualTo(idempotencyKey);
        }

        @Test
        @DisplayName("Should throw exception when amount is null")
        void shouldThrowExceptionWhenAmountIsNull() {
            // Arrange
            CreateOrderRequest request = new CreateOrderRequest(null, "GBP");

            // Act & Assert
            assertThatThrownBy(() -> orderService.createOrder(request, "test-key"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Amount is required");

            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw exception when amount is zero")
        void shouldThrowExceptionWhenAmountIsZero() {
            // Arrange
            CreateOrderRequest request = new CreateOrderRequest(BigDecimal.ZERO, "GBP");

            // Act & Assert
            assertThatThrownBy(() -> orderService.createOrder(request, "test-key"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Amount must be greater than zero");

            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw exception when amount is negative")
        void shouldThrowExceptionWhenAmountIsNegative() {
            // Arrange
            CreateOrderRequest request = new CreateOrderRequest(new BigDecimal("-10.00"), "GBP");

            // Act & Assert
            assertThatThrownBy(() -> orderService.createOrder(request, "test-key"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Amount must be greater than zero");

            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw exception when currency is null")
        void shouldThrowExceptionWhenCurrencyIsNull() {
            // Arrange
            CreateOrderRequest request = new CreateOrderRequest(new BigDecimal("29.99"), null);

            // Act & Assert
            assertThatThrownBy(() -> orderService.createOrder(request, "test-key"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Currency is required");

            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw exception when currency is empty")
        void shouldThrowExceptionWhenCurrencyIsEmpty() {
            // Arrange
            CreateOrderRequest request = new CreateOrderRequest(new BigDecimal("29.99"), "");

            // Act & Assert
            assertThatThrownBy(() -> orderService.createOrder(request, "test-key"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Currency is required");

            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw exception when idempotency key contains invalid characters")
        void shouldThrowExceptionWhenIdempotencyKeyIsInvalid() {
            // Arrange
            CreateOrderRequest request = new CreateOrderRequest(new BigDecimal("29.99"), "GBP");
            String invalidKey = "test-key@#$%";

            // Act & Assert
            assertThatThrownBy(() -> orderService.createOrder(request, invalidKey))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Idempotency key can only contain alphanumeric characters");

            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw exception when idempotency key exceeds 64 characters")
        void shouldThrowExceptionWhenIdempotencyKeyIsTooLong() {
            // Arrange
            CreateOrderRequest request = new CreateOrderRequest(new BigDecimal("29.99"), "GBP");
            String tooLongKey = "a".repeat(65);

            // Act & Assert
            assertThatThrownBy(() -> orderService.createOrder(request, tooLongKey))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Idempotency key must not exceed 64 characters");

            verify(orderRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Get Order Tests")
    class GetOrderTests {

        @Test
        @DisplayName("Should return order when ID exists")
        void shouldReturnOrderWhenIdExists() {
            // Arrange
            Long orderId = 1L;
            Order order = new Order();
            order.setId(orderId);
            order.setAmount(new BigDecimal("29.9900"));
            order.setCurrency("GBP");
            order.setStatus(OrderStatus.PAID);
            order.setIdempotencyKey("test-key");
            order.setCreatedAt(LocalDateTime.now());

            when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

            // Act
            OrderResponse response = orderService.getOrderById(orderId);

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.id()).isEqualTo(orderId);
            assertThat(response.status()).isEqualTo("PAID");

            verify(orderRepository).findById(orderId);
        }

        @Test
        @DisplayName("Should throw OrderNotFoundException when ID does not exist")
        void shouldThrowExceptionWhenIdDoesNotExist() {
            // Arrange
            Long nonExistentId = 999L;
            when(orderRepository.findById(nonExistentId)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> orderService.getOrderById(nonExistentId))
                    .isInstanceOf(OrderNotFoundException.class)
                    .hasMessageContaining("Order not found with ID: 999");

            verify(orderRepository).findById(nonExistentId);
        }
    }
}