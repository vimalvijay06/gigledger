package com.gigledger.repository;

import com.gigledger.entity.PlatformGrievanceContact;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface PlatformGrievanceContactRepository extends JpaRepository<PlatformGrievanceContact, UUID> {
    Optional<PlatformGrievanceContact> findByPlatformNameIgnoreCase(String platformName);
}
