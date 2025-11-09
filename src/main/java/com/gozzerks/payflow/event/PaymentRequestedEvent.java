package com.gozzerks.payflow.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public class PaymentRequestedEvent {
    private UUID orderId;
    private BigDecimal amount;
    private String currency;
    private String idempotencyKey;
    private LocalDateTime timestamp;
    private int retryCount;

    public PaymentRequestedEvent() {
    }

    public PaymentRequestedEvent(UUID orderId, BigDecimal amount, String currency, String idempotencyKey) {
        this.orderId = orderId;
        this.amount = amount;
        this.currency = currency;
        this.idempotencyKey = idempotencyKey;
        this.timestamp = LocalDateTime.now();
        this.retryCount = 0;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public void setOrderId(UUID orderId) {
        this.orderId = orderId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    @Override
    public String toString() {
        return "PaymentRequestedEvent{" +
                "orderId=" + orderId +
                ", amount=" + amount +
                ", currency='" + currency + '\'' +
                ", idempotencyKey='" + idempotencyKey + '\'' +
                ", timestamp=" + timestamp +
                ", retryCount=" + retryCount +
                '}';
    }
}