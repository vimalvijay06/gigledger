package com.gigledger.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "draft_complaints",
    indexes = {
        @Index(name = "idx_complaint_user_status", columnList = "user_id, status")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DraftComplaint {

    @Id
    @UuidGenerator
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "discrepancy_flag_id", nullable = false)
    private DiscrepancyFlag discrepancyFlag;

    @Column(name = "platform_name", nullable = false, length = 64)
    private String platformName;

    @Column(name = "grievance_email", nullable = false, length = 128)
    private String grievanceEmail;

    @Column(nullable = false, length = 256)
    private String subject;

    @Column(name = "draft_text", nullable = false, columnDefinition = "TEXT")
    private String draftText;

    // PENDING_REVIEW, SENT, DISMISSED
    @Column(nullable = false, length = 24)
    @Builder.Default
    private String status = "PENDING_REVIEW";

    @Column(name = "dismiss_reason", length = 256)
    private String dismissReason;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
