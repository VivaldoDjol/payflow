package com.gozzerks.payflow.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "Response object containing payment order details")
public record OrderResponse(
        @Schema(description = "Unique identifier for the order", example = "12345")
        Long id,

        @Schema(description = "Payment amount", example = "29.99")
        BigDecimal amount,

        @Schema(description = "ISO 4217 currency code", example = "GBP")
        String currency,

        @Schema(description = "Current status of the order", example = "PROCESSING")
        String status,

        @Schema(description = "Idempotency key used for this order", example = "key123")
        String idempotencyKey,

        @Schema(description = "Timestamp when the order was created", example = "2023-06-15T14:30:00")
        LocalDateTime createdAt
) {}