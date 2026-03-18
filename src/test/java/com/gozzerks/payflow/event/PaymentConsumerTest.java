package com.gozzerks.payflow.event;

import com.gozzerks.payflow.service.PaymentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import org.springframework.amqp.AmqpRejectAndDontRequeueException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentConsumer Tests")
class PaymentConsumerTest {

    @Mock
    private PaymentService paymentService;

    @InjectMocks
    private PaymentConsumer paymentConsumer;

    @Test
    @DisplayName("Should process payment request successfully")
    void shouldProcessPaymentRequestSuccessfully() {
        // Arrange
        PaymentRequestedEvent event = new PaymentRequestedEvent(
                1L,
                new BigDecimal("29.99"),
                "GBP",
                "test-key-123"
        );

        doNothing().when(paymentService).processPayment(1L);

        // Act
        paymentConsumer.handlePaymentRequest(event);

        // Assert
        verify(paymentService, times(1)).processPayment(1L);
    }

    @Test
    @DisplayName("Should handle payment processing failure")
    void shouldHandlePaymentProcessingFailure() {
        // Arrange
        PaymentRequestedEvent event = new PaymentRequestedEvent(
                1L,
                new BigDecimal("29.99"),
                "GBP",
                "test-key-123"
        );

        doThrow(new RuntimeException("Payment gateway error"))
                .when(paymentService).processPayment(1L);

        // Act & Assert - Wrapped as AmqpRejectAndDontRequeueException to route to DLQ
        assertThatThrownBy(() -> paymentConsumer.handlePaymentRequest(event))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class)
                .hasCauseInstanceOf(RuntimeException.class)
                .cause().hasMessageContaining("Payment gateway error");

        verify(paymentService, times(1)).processPayment(1L);
    }

    @Test
    @DisplayName("Should extract correct order ID from event")
    void shouldExtractCorrectOrderIdFromEvent() {
        // Arrange
        Long expectedOrderId = 42L;
        PaymentRequestedEvent event = new PaymentRequestedEvent(
                expectedOrderId,
                new BigDecimal("100.00"),
                "USD",
                "test-key-456"
        );

        doNothing().when(paymentService).processPayment(expectedOrderId);

        // Act
        paymentConsumer.handlePaymentRequest(event);

        // Assert
        verify(paymentService).processPayment(expectedOrderId);
        verifyNoMoreInteractions(paymentService);
    }
}