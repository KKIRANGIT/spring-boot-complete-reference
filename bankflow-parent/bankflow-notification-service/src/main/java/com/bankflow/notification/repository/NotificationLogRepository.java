package com.bankflow.notification.repository;

import com.bankflow.notification.entity.NotificationLog;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for durable notification audit logs.
 */
public interface NotificationLogRepository extends JpaRepository<NotificationLog, UUID> {
}
