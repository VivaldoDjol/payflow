package com.gozzerks.payflow.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

@Schema(description = "Request object for creating a new payment order")
public record CreateOrderRequest(
        @DecimalMin(value = "0.01", message = "Amount must be at least 0.01")
        @DecimalMax(value = "177777.7777", message = "Amount exceeds maximum allowed value of 177777.7777")
        @Schema(
                description = "Payment amount",
                example = "29.99",
                minimum = "0.01",
                maximum = "177777.7777"
        )
        BigDecimal amount,

        @NotBlank(message = "Currency is required")
        @Pattern(regexp = "[A-Z]{3}", message = "Currency must be ISO 4217 code (e.g., GBP)")
        @Schema(
                description = "ISO 4217 currency code",
                example = "GBP",
                pattern = "[A-Z]{3}"
        )
        String currency
) {}