package com.bankflow.account.repository;

import com.bankflow.account.entity.AccountAuditLog;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for account audit history lookups.
 *
 * <p>Plain English: this is how account-service reads the append-only statement trail.
 */
public interface AccountAuditLogRepository extends JpaRepository<AccountAuditLog, UUID> {

  /**
   * Returns the latest audit entries first so statements read like a real bank ledger.
   */
  Page<AccountAuditLog> findByAccountIdOrderByPerformedAtDesc(UUID accountId, Pageable pageable);
}
