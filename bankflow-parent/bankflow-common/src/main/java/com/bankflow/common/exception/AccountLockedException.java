package com.bankflow.common.exception;

import java.time.LocalDateTime;

/**
 * Raised when a user account is temporarily locked.
 *
 * <p>Design decision: the exception includes the username and lock expiry so support teams can
 * diagnose temporary lockouts quickly.
 *
 * <p>Interview answer: this class shows how to model authentication throttling and abuse control
 * as explicit domain behavior.
 *
 * <p>Security issue prevented: conflating lockouts with ordinary authentication failures can hide
 * brute-force patterns and make lock policies harder to enforce.
 */
public class AccountLockedException extends RuntimeException {

  public AccountLockedException(String username, LocalDateTime lockedUntil) {
    super("Account locked for user " + username + " until " + lockedUntil);
  }
}
