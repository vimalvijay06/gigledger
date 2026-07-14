package com.gigledger.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "fuel_price_cache",
    indexes = {
        @Index(name = "idx_fuel_district_date", columnList = "district, fetched_date")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FuelPriceCache {

    @Id
    @UuidGenerator
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false, length = 128)
    private String district;

    @Column(name = "petrol_price", nullable = false, precision = 6, scale = 2)
    private BigDecimal petrolPrice;

    @Column(name = "fetched_date", nullable = false)
    private LocalDate fetchedDate;

    @Column(name = "is_verified", nullable = false)
    private boolean verified;
}
