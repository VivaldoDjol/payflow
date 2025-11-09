package com.gozzerks.payflow.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class PaymentConsumer {

    private static final Logger logger = LoggerFactory.getLogger(PaymentConsumer.class);


    public PaymentConsumer() {

    }

    @RabbitListener(queues = "payment.request.queue")
    public void handlePaymentRequest(PaymentRequestedEvent event) {
        logger.info("Received payment request for Order ID: {} with amount {} {}",
                event.getOrderId(), event.getAmount(), event.getCurrency());


        logger.debug("Payment event details: {}", event);
    }
}