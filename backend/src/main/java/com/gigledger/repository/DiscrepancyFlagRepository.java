package com.gigledger.repository;

import com.gigledger.entity.DiscrepancyFlag;
import com.gigledger.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface DiscrepancyFlagRepository extends JpaRepository<DiscrepancyFlag, UUID> {

    // Returns all flags for a user, newest first (by period_start)
    List<DiscrepancyFlag> findByUserOrderByPeriodStartDesc(User user);

    // Used by the scheduler to avoid creating duplicate flags for the same period
    boolean existsByUserAndPeriodStartAndPeriodEndAndBucket(
        User user, LocalDate periodStart, LocalDate periodEnd, String bucket);
}
