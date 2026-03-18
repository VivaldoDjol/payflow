package com.gozzerks.payflow.event;

import com.gozzerks.payflow.service.PaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class PaymentConsumer {

    private static final Logger log = LoggerFactory.getLogger(PaymentConsumer.class);
    private final PaymentService paymentService;

    public PaymentConsumer(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @RabbitListener(queues = "payment.queue")
    public void handlePaymentRequest(PaymentRequestedEvent event) {
        log.info("Processing payment for order {} (idempotencyKey: {}) ({} {}) ",
                event.orderId(), event.idempotencyKey(), event.amount(), event.currency());
        try {
            paymentService.processPayment(event.orderId());
        } catch (RuntimeException ex) {
            log.error("Payment processing failed for order {} (idempotencyKey: {}), routing to DLQ: {}", event.orderId(), event.idempotencyKey(), ex.getMessage());
            throw new AmqpRejectAndDontRequeueException(ex);
        }
    }
}