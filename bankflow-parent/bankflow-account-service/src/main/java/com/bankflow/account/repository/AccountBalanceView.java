package com.bankflow.account.repository;

import com.bankflow.common.domain.AccountStatus;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Lightweight projection for the short-lived balance read model.
 *
 * <p>Plain English: this gives the query service just the fields needed for a balance lookup
 * without hydrating the full account aggregate.
 *
 * <p>Design decision: CQRS read paths should be explicit about the columns they need so high-volume
 * reads stay cheap and predictable.
 *
 * <p>Interview question answered: "How do you avoid loading full JPA entities for simple read
 * models in a banking service?"
 */
public interface AccountBalanceView {

  /**
   * Returns the account identifier for the cached balance response.
   */
  UUID getAccountId();

  /**
   * Returns the exact BigDecimal balance snapshot.
   */
  BigDecimal getBalance();

  /**
   * Returns the account currency for UI display.
   */
  String getCurrency();

  /**
   * Returns the current lifecycle status alongside the balance.
   */
  AccountStatus getStatus();
}
