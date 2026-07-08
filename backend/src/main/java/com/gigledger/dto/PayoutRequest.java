package com.gigledger.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Request body for POST /tasks/{id}/payout.
 *
 * Validation rules:
 * - actualAmount:    required, must be >= 0
 *                   (zero is valid — a worker could receive ₹0 due to full cancellation)
 * - deductionReason: optional free-text; the platform's stated reason for any shortfall
 *
 * @PositiveOrZero allows zero (unlike @Positive) because a complete cancellation
 * payout of ₹0 is a legitimate and important data point to record.
 */
@Data
public class PayoutRequest {

    @NotNull(message = "Actual amount is required")
    @PositiveOrZero(message = "Actual amount cannot be negative")
    @DecimalMax(value = "999999.99", message = "Actual amount seems unrealistically large")
    private BigDecimal actualAmount;

    private String deductionReason; // optional — may be null or empty
}
