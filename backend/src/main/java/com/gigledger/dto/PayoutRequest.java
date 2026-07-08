package com.gigledger.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Request body for POST /tasks/{id}/payout — logging the actual amount received.
 *
 * - actualAmount: what actually landed in the worker's wallet
 * - deductionReason: the platform's stated reason for any shortfall (optional)
 */
@Data
public class PayoutRequest {

    @NotNull(message = "Actual amount is required")
    @DecimalMin(value = "0.0", message = "Actual amount cannot be negative")
    private BigDecimal actualAmount;

    // Optional — worker may not know or the platform may not have given a reason
    private String deductionReason;
}
