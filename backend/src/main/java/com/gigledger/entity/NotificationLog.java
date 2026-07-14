package com.gigledger.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Audit log entity representing a notification attempt via Email.
 */
@Entity
@Table(name = "notification_logs",
    indexes = {
        @Index(name = "idx_notif_user_channel", columnList = "user_id, channel")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationLog {

    @Id
    @UuidGenerator
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 16)
    private String channel; // "EMAIL"

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType; // "DISCREPANCY_FLAG" or "COMPLAINT_DRAFT"

    @Column(name = "sent_at", nullable = false)
    private LocalDateTime sentAt;

    @Column(name = "external_message_id", length = 128)
    private String externalMessageId; // Resend Email ID

    @Column(nullable = false, length = 32)
    private String status; // "SUCCESS", "FAILED", "SKIPPED_USER_PREF"

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;
}
