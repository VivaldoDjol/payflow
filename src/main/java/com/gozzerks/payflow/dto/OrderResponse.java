package com.gozzerks.payflow.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record OrderResponse(
        Long id,
        BigDecimal amount,
        String currency,
        String status,
        String idempotencyKey,
        LocalDateTime createdAt
) {}