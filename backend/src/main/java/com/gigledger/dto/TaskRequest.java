package com.gigledger.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Request body for POST /tasks — manually logging a new delivery task.
 *
 * - promisedAmount: the fare shown on the app before the worker accepted (₹)
 * - distanceKm: the delivery distance shown at acceptance
 * - acceptedAt: when the worker accepted; sent by the client so workers can
 *   log tasks retroactively from memory if they forgot to log in real-time
 */
@Data
public class TaskRequest {

    @NotNull(message = "Promised amount is required")
    @DecimalMin(value = "0.01", message = "Promised amount must be positive")
    private BigDecimal promisedAmount;

    @DecimalMin(value = "0.0", message = "Distance cannot be negative")
    private BigDecimal distanceKm;

    @NotNull(message = "Accepted-at timestamp is required")
    private LocalDateTime acceptedAt;
}
