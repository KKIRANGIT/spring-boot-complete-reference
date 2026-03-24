package com.bankflow.common.exception;

/**
 * Raised when authentication fails or the caller is not allowed to continue.
 *
 * <p>Design decision: authentication failures use a dedicated exception instead of generic runtime
 * errors so the API always returns a proper 401 contract.
 *
 * <p>Interview answer: this class demonstrates how to separate security failures from business
 * validation failures in a banking API.
 *
 * <p>Security issue prevented: if authentication problems fall through to generic exception
 * handling, clients receive misleading 500 responses and operators lose visibility into auth abuse.
 */
public class UnauthorizedException extends RuntimeException {

  /**
   * Creates a 401-style exception with a caller-safe message.
   *
   * <p>Plain English: this constructor stores the reason the request was rejected without exposing
   * internal stack trace details.
   *
   * <p>Design decision: the message stays human-readable because auth failures often need to be
   * surfaced to clients or audit logs.
   *
   * <p>Interview question answered: "How do you return consistent authentication errors in a REST
   * API?"
   */
  public UnauthorizedException(String message) {
    super(message);
  }
}
