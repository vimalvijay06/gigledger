package com.gigledger.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Request body for POST /tasks.
 *
 * Validation rules:
 * - promisedAmount: required, must be > 0 (a fare of ₹0 makes no sense)
 * - distanceKm:     optional, but if provided must be >= 0
 * - acceptedAt:     required — must be an explicit timestamp, not defaulted
 *                   server-side, so workers can backdate retroactive entries
 *
 * @Positive is stricter than @DecimalMin("0.01") — it rejects 0 and negatives
 * in a single annotation and works correctly with BigDecimal.
 */
@Data
public class TaskRequest {

    @NotNull(message = "Promised amount is required")
    @Positive(message = "Promised amount must be greater than zero")
    @DecimalMax(value = "999999.99", message = "Promised amount seems unrealistically large")
    private BigDecimal promisedAmount;

    @PositiveOrZero(message = "Distance cannot be negative")
    @DecimalMax(value = "9999.99", message = "Distance seems unrealistically large")
    private BigDecimal distanceKm; // optional

    @NotNull(message = "Accepted-at timestamp is required")
    private LocalDateTime acceptedAt;
}
