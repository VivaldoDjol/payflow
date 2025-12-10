package com.gozzerks.payflow.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "orders", uniqueConstraints = @UniqueConstraint(columnNames = "idempotency_key"))
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, precision = 19, scale = 4)
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    @DecimalMax(value = "177777.7777", message = "Amount exceeds maximum allowed value of 177777.7777")
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private OrderStatus status = OrderStatus.PENDING;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 64)
    @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "Idempotency key can only contain alphanumeric characters, underscores, and hyphens")
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Order() {}

    public Order(BigDecimal amount, String currency, String idempotencyKey) {
        this.amount = amount;
        this.currency = currency;
        this.idempotencyKey = idempotencyKey;
    }

    // Getters
    public Long getId() { return id; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public OrderStatus getStatus() { return status; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    // Setters
    public void setId(Long id) { this.id = id; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public void setCurrency(String currency) { this.currency = currency; }
    public void setStatus(OrderStatus status) { this.status = status; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Order order)) return false;
        return Objects.equals(idempotencyKey, order.idempotencyKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(idempotencyKey);
    }
}