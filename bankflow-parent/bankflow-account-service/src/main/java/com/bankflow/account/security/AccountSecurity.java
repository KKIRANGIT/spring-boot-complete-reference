package com.bankflow.account.security;

import com.bankflow.account.repository.AccountRepository;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * Method-security helper for account ownership checks.
 *
 * <p>Plain English: this lets controller methods ask, "Does the authenticated user own this
 * account?" without embedding repository logic into SpEL expressions.
 */
@Component("accountSecurity")
public class AccountSecurity {

  private final AccountRepository accountRepository;

  public AccountSecurity(AccountRepository accountRepository) {
    this.accountRepository = accountRepository;
  }

  /**
   * Returns whether the current authenticated user owns the target account.
   */
  public boolean isOwner(Authentication authentication, UUID accountId) {
    if (authentication == null || !authentication.isAuthenticated()) {
      return false;
    }

    try {
      UUID currentUserId = UUID.fromString(authentication.getName());
      return accountRepository.findUserIdById(accountId)
          .map(currentUserId::equals)
          .orElse(false);
    } catch (IllegalArgumentException ex) {
      return false;
    }
  }
}
