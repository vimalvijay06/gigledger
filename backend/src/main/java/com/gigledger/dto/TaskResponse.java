package com.gigledger.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response body for GET /tasks — a flattened view of a task and its payout.
 *
 * Rather than nesting Task inside Payout and returning raw entities
 * (which would expose internal fields and trigger lazy-load issues),
 * we manually map to this flat DTO in the service layer.
 *
 * difference = promisedAmount - actualAmount
 *   - Positive → worker was underpaid
 *   - Zero      → exact match
 *   - Negative  → worker was overpaid (rare, but possible with bonuses)
 *
 * payoutLogged: a boolean flag so the frontend can easily decide whether
 *   to show a "Log Payout" button or the actual/difference values.
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
}
