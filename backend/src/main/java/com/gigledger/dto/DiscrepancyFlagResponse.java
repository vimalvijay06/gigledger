package com.gigledger.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class DiscrepancyFlagResponse {
    private UUID      id;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private String    bucket;         // "peak" or "off_peak"
    private BigDecimal baselineRate;  // Rs/km rolling mean (worker's normal)
    private BigDecimal observedRate;  // Rs/km actually seen during flagged week
    private String    severity;       // "low" | "medium" | "high"
    private BigDecimal sdBelow;       // standard deviations below baseline
    private LocalDateTime createdAt;
}
