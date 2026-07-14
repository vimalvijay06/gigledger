package com.gigledger.repository;

import com.gigledger.entity.FuelCostFlag;
import com.gigledger.entity.Task;
import com.gigledger.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FuelCostFlagRepository extends JpaRepository<FuelCostFlag, UUID> {
    Optional<FuelCostFlag> findByTask(Task task);
    List<FuelCostFlag> findByUser(User user);
    void deleteByTask(Task task);
}
