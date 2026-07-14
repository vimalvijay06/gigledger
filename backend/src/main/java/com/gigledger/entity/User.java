package com.gigledger.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Represents a registered gig worker.
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @UuidGenerator
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private String name;

    @Column(unique = true)
    private String phoneNumber;

    @Column(length = 10)
    @Builder.Default
    private String languagePref = "en";

    @Column(name = "platform_preference", length = 32)
    @Builder.Default
    private String platformPreference = "Swiggy";

    @Column(name = "shortfall_threshold", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal shortfallThreshold = new BigDecimal("500.00");

    @Column(name = "severity_threshold", length = 8)
    @Builder.Default
    private String severityThreshold = "high";

    @Column(name = "email_notifications_enabled", nullable = false)
    @Builder.Default
    private boolean emailNotificationsEnabled = true;

    // Fuel cost inputs
    @Column(name = "district", length = 64)
    @Builder.Default
    private String district = "Chennai";

    @Column(name = "state", length = 64)
    @Builder.Default
    private String state = "Tamil Nadu";

    @Column(name = "vehicle_type", length = 32)
    @Builder.Default
    private String vehicleType = "bike";

    @Column(name = "fuel_efficiency", precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal fuelEfficiency = new BigDecimal("45.00");

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
