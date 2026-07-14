package com.gigledger.repository;

import com.gigledger.entity.NotificationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

/**
 * Spring Data JPA repository for NotificationLog.
 */
public interface NotificationLogRepository extends JpaRepository<NotificationLog, UUID> {
}
