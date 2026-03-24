package com.bankflow.common.exception;

import java.util.UUID;

/**
 * Raised when an operation targets an account that has been frozen.
 *
 * <p>Design decision: frozen accounts are modeled explicitly because they represent a compliance
 * or fraud-control state, not a generic validation error.
 *
 * <p>Interview answer: this class shows that operational account states deserve their own domain
 * exceptions and policies.
 *
 * <p>Security issue prevented: clear state-specific handling avoids accidentally permitting money
 * movement by treating a frozen account as merely inactive.
 */
public class AccountFrozenException extends RuntimeException {

  public AccountFrozenException(UUID accountId) {
    super("Account is frozen: " + accountId);
  }
}
