package com.gigledger.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "record_hash_chains",
    indexes = {
        @Index(name = "idx_chain_user_id", columnList = "user_id"),
        @Index(name = "idx_chain_record", columnList = "record_type, record_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecordHashChain {

    @Id
    @UuidGenerator
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // "task", "payout", or "flag"
    @Column(nullable = false, length = 16)
    private String recordType;

    // The ID of the original Task, Payout, or DiscrepancyFlag entity
    @Column(nullable = false)
    private UUID recordId;

    // SHA-256 hash of this record's key fields + the previous entry's hash
    @Column(nullable = false, length = 64)
    private String recordHash;

    // Hash of the previous record in the chain (null for the very first record)
    @Column(length = 64)
    private String previousHash;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
