package com.bankflow.common.exception;

/**
 * Raised when the same idempotency key is replayed for a conflicting request.
 *
 * <p>Design decision: idempotency failures deserve their own exception because payment retries are
 * expected and must be distinguished from random server errors.
 *
 * <p>Interview answer: this class shows how to build safe retry behavior for money movement APIs.
 *
 * <p>Bug prevented: without an explicit conflict signal, duplicate client retries can create
 * double-processing risk or confusing inconsistent responses.
 */
public class IdempotencyConflictException extends RuntimeException {

  public IdempotencyConflictException(String idempotencyKey) {
    super("Idempotency conflict detected for key: " + idempotencyKey);
  }
}
