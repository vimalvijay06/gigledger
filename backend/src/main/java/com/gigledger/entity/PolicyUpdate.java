package com.gigledger.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "policy_updates",
    uniqueConstraints = {
        @UniqueConstraint(name = "uc_policy_source_url", columnNames = "source_url")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PolicyUpdate {

    @Id
    @UuidGenerator
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false, length = 512)
    private String title;

    @Column(name = "source_url", nullable = false, length = 1024)
    private String sourceUrl;

    @Column(name = "raw_content", nullable = false, columnDefinition = "TEXT")
    private String rawContent;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(length = 64, nullable = false, columnDefinition = "varchar(64) default 'POLICY_BENEFITS'")
    @Builder.Default
    private String category = "POLICY_BENEFITS";

    @Column(length = 32, nullable = false, columnDefinition = "varchar(32) default 'NORMAL'")
    @Builder.Default
    private String urgency = "NORMAL";

    @Column(columnDefinition = "TEXT")
    private String reasoning;

    @Column(name = "category_hint", length = 64)
    private String categoryHint;

    // Language of translation if narrated/translated (e.g. "en", "ta", "hi")
    @Column(length = 8)
    private String lang;

    @CreationTimestamp
    @Column(name = "fetched_at", updatable = false)
    private LocalDateTime fetchedAt;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(nullable = false)
    @Builder.Default
    private boolean excluded = false;
}
