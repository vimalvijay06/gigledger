package com.gigledger.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Represents a single delivery/gig task accepted by a worker.
 *
 * - promisedAmount: the fare shown to the worker before accepting (₹)
 * - distanceKm: the delivery distance at the time of acceptance
 * - acceptedAt: when the worker accepted the task (user-supplied, not server time)
 * - screenshotUrl / ocrConfidence: nullable placeholders for Phase 2 OCR pipeline
 *
 * The actual payout is in a separate Payout entity (OneToOne) so that a task
 * can exist in a "pending" state (no payout logged yet).
 */
@Entity
@Table(name = "tasks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Task {

    @Id
    @UuidGenerator
    @Column(updatable = false, nullable = false)
    private UUID id;

    // Many tasks belong to one user
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal promisedAmount;

    @Column(precision = 8, scale = 2)
    private BigDecimal distanceKm;

    @Column(nullable = false)
    private LocalDateTime acceptedAt;

    // --- Phase 2 placeholders (nullable, no logic built yet) ---
    @Column(length = 512)
    private String screenshotUrl;

    // Confidence score from OCR model (0.0 – 1.0), null until Phase 2
    @Column(precision = 5, scale = 4)
    private BigDecimal ocrConfidence;

    // Bidirectional link to payout; mappedBy means Payout owns the FK
    @OneToOne(mappedBy = "task", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    private Payout payout;
}
