package com.bankflow.account.repository;

import com.bankflow.account.entity.Account;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repository for the account write model and CQRS query lookups.
 *
 * <p>Plain English: this interface loads accounts by id, owner, number, and balance projections.
 *
 * <p>Interview question answered: "How do you keep account-service queries explicit without
 * joining across other microservice databases?"
 */
public interface AccountRepository extends JpaRepository<Account, UUID> {

  /**
   * Checks account-number uniqueness during account creation.
   */
  boolean existsByAccountNumber(String accountNumber);

  /**
   * Returns all accounts owned by one user in most-recent-first order.
   */
  List<Account> findByUserIdOrderByCreatedAtDesc(UUID userId);

  /**
   * Reads the short-lived balance view for a cheap CQRS balance query.
   */
  @Query("""
      select
        a.id as accountId,
        a.balance as balance,
        a.currency as currency,
        a.status as status
      from Account a
      where a.id = :id
      """)
  Optional<AccountBalanceView> findBalanceById(@Param("id") UUID id);

  /**
   * Reads only the owner id for authorization checks.
   */
  @Query("select a.userId from Account a where a.id = :id")
  Optional<UUID> findUserIdById(@Param("id") UUID id);
}
