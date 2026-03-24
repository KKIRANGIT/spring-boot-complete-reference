package com.bankflow.common.exception;

/**
 * Validation exception for business-rule failures that are not simple bean-validation annotations.
 *
 * <p>Plain English: this is used when a request is syntactically valid JSON but semantically wrong,
 * such as attempting to transfer a non-positive amount or sending money to the same account.
 *
 * <p>Design decision: a dedicated runtime exception keeps business validation mapped to HTTP 400
 * instead of falling into the generic 500 handler.
 *
 * <p>Interview question answered: "How do you distinguish invalid business input from unexpected
 * server failures in a banking API?"
 */
public class ValidationException extends RuntimeException {

  public ValidationException(String message) {
    super(message);
  }
}
