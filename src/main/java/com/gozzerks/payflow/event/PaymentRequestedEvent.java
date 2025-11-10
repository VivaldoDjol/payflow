package com.gozzerks.payflow.event;

import java.math.BigDecimal;

public record PaymentRequestedEvent(
        Long orderId,
        BigDecimal amount,
        String currency,
        String idempotencyKey
) {}