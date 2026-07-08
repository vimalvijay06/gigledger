package com.gigledger.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Records the actual amount the worker was paid for a task.
 *
 * - actualAmount: what actually appeared in the worker's wallet
 * - deductionReason: optional text — the platform's stated reason for any deduction
 *   (e.g., "weather surcharge reversal"); nullable because the worker may not know the reason
 * - loggedAt: server time when the payout was recorded
 *
 * OneToOne with Task: one task can have at most one payout record.
 * The FK (task_id) lives in this table (the "owning" side).
 */
@Entity
@Table(name = "payouts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payout {

    @Id
    @UuidGenerator
    @Column(updatable = false, nullable = false)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_id", nullable = false, unique = true)
    private Task task;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal actualAmount;

    // Worker can note the platform's stated reason for any deduction; may be blank
    @Column(columnDefinition = "TEXT")
    private String deductionReason;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime loggedAt;
}
