package com.gigledger.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "fuel_cost_flags",
    indexes = {
        @Index(name = "idx_fuel_flag_user_id", columnList = "user_id"),
        @Index(name = "idx_fuel_flag_task_id", columnList = "task_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FuelCostFlag {

    @Id
    @UuidGenerator
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_id", nullable = false)
    private Task task;

    @Column(name = "estimated_fuel_cost", nullable = false, precision = 10, scale = 2)
    private BigDecimal estimatedFuelCost;

    @Column(name = "petrol_price_used", nullable = false, precision = 6, scale = 2)
    private BigDecimal petrolPriceUsed;

    @Column(name = "fuel_cost_ratio", nullable = false, precision = 8, scale = 4)
    private BigDecimal fuelCostRatio;

    @Column(nullable = false, length = 8)
    private String severity;

    @Column(name = "is_verified_price", nullable = false)
    private boolean verifiedPrice;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
