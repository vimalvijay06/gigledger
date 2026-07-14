package com.gigledger.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "discrepancy_flags",
    indexes = {
        @Index(name = "idx_flag_user_id", columnList = "user_id"),
        @Index(name = "idx_flag_period",  columnList = "period_start, period_end")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DiscrepancyFlag {

    @Id
    @UuidGenerator
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // ISO week start and end dates of the flagged period
    @Column(nullable = false)
    private LocalDate periodStart;

    @Column(nullable = false)
    private LocalDate periodEnd;

    // "peak" or "off_peak"
    @Column(nullable = false, length = 16)
    private String bucket;

    // Rolling baseline Rs/km for this bucket/window
    @Column(nullable = false, precision = 10, scale = 4)
    private BigDecimal baselineRate;

    // Observed Rs/km during the flagged week
    @Column(nullable = false, precision = 10, scale = 4)
    private BigDecimal observedRate;

    // "low" | "medium" | "high"
    @Column(nullable = false, length = 8)
    private String severity;

    // How many SDs below the baseline the observed rate fell
    @Column(nullable = false, precision = 8, scale = 4)
    private BigDecimal sdBelow;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
