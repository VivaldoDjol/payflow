package com.gozzerks.payflow.event;

import com.gozzerks.payflow.config.RabbitMQConfig;
import com.gozzerks.payflow.service.PaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class PaymentConsumer {

    private static final Logger log = LoggerFactory.getLogger(PaymentConsumer.class);
    private final PaymentService paymentService;

    public PaymentConsumer(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @RabbitListener(queues = "payment.queue")
    public void handlePaymentRequest(PaymentRequestedEvent event, Message message) {
        long retryCount = getRetryCount(message);
        log.info("Processing payment for order {} (attempt {}, idempotencyKey: {}) ({} {})",
                event.orderId(), retryCount + 1, event.idempotencyKey(), event.amount(), event.currency());

        if (retryCount >= RabbitMQConfig.MAX_RETRIES) {
            log.error("Max retries ({}) exceeded for order {}, marking as FAILED",
                    RabbitMQConfig.MAX_RETRIES, event.orderId());
            paymentService.markAsFailed(event.orderId());
            return;
        }

        try {
            paymentService.processPayment(event.orderId());
        } catch (RuntimeException ex) {
            log.error("Payment processing failed for order {} (attempt {}), routing to DLQ: {}",
                    event.orderId(), retryCount + 1, ex.getMessage());
            throw new AmqpRejectAndDontRequeueException(ex);
        }
    }

    @SuppressWarnings("unchecked")
    private long getRetryCount(Message message) {
        List<Map<String, ?>> xDeath = (List<Map<String, ?>>)
                message.getMessageProperties().getHeaders().get("x-death");
        if (xDeath == null || xDeath.isEmpty()) {
            return 0;
        }
        return xDeath.stream()
                .filter(entry -> RabbitMQConfig.PAYMENT_QUEUE.equals(entry.get("queue")))
                .filter(entry -> "rejected".equals(entry.get("reason")))
                .mapToLong(entry -> ((Number) entry.get("count")).longValue())
                .sum();
    }
}
