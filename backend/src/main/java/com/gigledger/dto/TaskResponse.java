package com.gigledger.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response body for GET /tasks — a flattened view of a task and its payout.
 */
@Data
@Builder
public class TaskResponse {
    private UUID id;
    private LocalDateTime acceptedAt;
    private BigDecimal promisedAmount;
    private BigDecimal distanceKm;

    // null if payout hasn't been logged yet
    private BigDecimal actualAmount;
    private String deductionReason;
    private LocalDateTime payoutLoggedAt;

    // null if no payout logged yet; otherwise promisedAmount - actualAmount
    private BigDecimal difference;

    private boolean payoutLogged;

    // Fuel Cost check fields
    private BigDecimal estimatedFuelCost;
    private BigDecimal petrolPriceUsed;
    private BigDecimal fuelCostRatio;
    private boolean fuelCostFlagged;
    private String fuelCostSeverity;
    private boolean verifiedPrice;
}
