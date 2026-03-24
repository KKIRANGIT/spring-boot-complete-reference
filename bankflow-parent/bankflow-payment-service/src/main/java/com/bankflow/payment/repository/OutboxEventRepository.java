package com.bankflow.payment.repository;

import com.bankflow.payment.domain.OutboxStatus;
import com.bankflow.payment.entity.OutboxEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for polling and updating pending outbox rows.
 */
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

  List<OutboxEvent> findByStatusAndRetryCountLessThanOrderByCreatedAtAsc(
      OutboxStatus status,
      int retryCount,
      Pageable pageable);
}
