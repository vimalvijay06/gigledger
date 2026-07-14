package com.gigledger.repository;

import com.gigledger.entity.FuelPriceCache;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FuelPriceCacheRepository extends JpaRepository<FuelPriceCache, UUID> {
    Optional<FuelPriceCache> findByDistrictIgnoreCaseAndFetchedDate(String district, LocalDate fetchedDate);
}
