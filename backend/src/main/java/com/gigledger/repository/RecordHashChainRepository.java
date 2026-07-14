package com.gigledger.repository;

import com.gigledger.entity.RecordHashChain;
import com.gigledger.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RecordHashChainRepository extends JpaRepository<RecordHashChain, UUID> {

    // Find latest block in user's chain
    Optional<RecordHashChain> findTopByUserOrderByCreatedAtDesc(User user);

    // Retrieve all blocks in order to verify
    List<RecordHashChain> findByUserOrderByCreatedAtAsc(User user);
}
