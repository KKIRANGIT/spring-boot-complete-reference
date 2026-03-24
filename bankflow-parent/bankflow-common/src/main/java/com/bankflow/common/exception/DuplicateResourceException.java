package com.bankflow.common.exception;

/**
 * Raised when a caller tries to create data that already exists.
 *
 * <p>Design decision: duplicate creation is a first-class business failure, not a generic runtime
 * error, so it receives a 409 response consistently.
 *
 * <p>Interview answer: this class demonstrates idempotent-friendly API design and conflict-aware
 * domain validation.
 *
 * <p>Bug prevented: callers can safely distinguish duplicates from server failures and avoid
 * retry storms that would otherwise create noisy repeated writes.
 */
public class DuplicateResourceException extends RuntimeException {

  public DuplicateResourceException(String message) {
    super(message);
  }
}
