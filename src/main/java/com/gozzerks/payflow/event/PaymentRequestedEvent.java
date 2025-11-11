package com.gozzerks.payflow.event;

import java.io.Serializable;
import java.math.BigDecimal;

public record PaymentRequestedEvent(
        Long orderId,
        BigDecimal amount,
        String currency,
        String idempotencyKey
) implements Serializable {}