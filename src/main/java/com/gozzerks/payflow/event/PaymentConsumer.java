package com.gozzerks.payflow.event;

import com.gozzerks.payflow.service.PaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
        log.info("📥 Processing payment for order {} ({} {})",
                event.orderId(), event.amount(), event.currency());
        paymentService.processPayment(event.orderId());
    }
}