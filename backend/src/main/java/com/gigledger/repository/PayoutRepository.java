package com.gigledger.repository;

import com.gigledger.entity.Payout;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Repository for Payout.
 * No custom queries needed yet — standard save/findById is sufficient for Phase 1.
 */
public interface PayoutRepository extends JpaRepository<Payout, UUID> {
}
