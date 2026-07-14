package com.gigledger.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class ReportDataResponse {

    // Worker Context
    private String workerName;
    private String workerEmail;
    private LocalDateTime generatedAt;

    // Integrity Status
    private boolean integrityValid;
    private int integrityTotalRecords;
    private UUID integrityBrokenAt;

    // Summary Statistics
    private int totalFlaggedPeriods;
    private BigDecimal totalShortfall; // Sum of task difference during flagged periods
    private LocalDate startDate;
    private LocalDate endDate;

    // Detail payload
    private List<FlaggedPeriodDetail> flaggedPeriods;
    private List<TaskResponse> allTasks;

    @Data
    @Builder
    public static class FlaggedPeriodDetail {
        private DiscrepancyFlagResponse flag;
        private List<TaskResponse> tasks;
    }
}
