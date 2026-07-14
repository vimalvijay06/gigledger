package com.gigledger.repository;

import com.gigledger.entity.PolicyUpdate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PolicyUpdateRepository extends JpaRepository<PolicyUpdate, UUID> {

    // Check if link was already ingested
    boolean existsBySourceUrl(String sourceUrl);

    // List all user-facing relevant feeds in reverse chronological order
    List<PolicyUpdate> findByExcludedFalseOrderByPublishedAtDesc();
}
