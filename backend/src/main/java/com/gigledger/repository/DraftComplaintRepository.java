package com.gigledger.repository;

import com.gigledger.entity.DraftComplaint;
import com.gigledger.entity.User;
import com.gigledger.entity.DiscrepancyFlag;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DraftComplaintRepository extends JpaRepository<DraftComplaint, UUID> {
    List<DraftComplaint> findByUserAndStatusOrderByCreatedAtDesc(User user, String status);
    Optional<DraftComplaint> findByDiscrepancyFlag(DiscrepancyFlag discrepancyFlag);
    boolean existsByDiscrepancyFlag(DiscrepancyFlag discrepancyFlag);
}
