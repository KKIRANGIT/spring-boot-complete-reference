package com.bankflow.common.error;

import org.springframework.http.HttpStatus;

/**
 * Shared BankFlow error catalog.
 *
 * <p>Design decision: errors are modeled as enums instead of free-form strings so every service
 * publishes the same values during REST responses, Kafka messages, and internal branching logic.
 *
 * <p>Interview answer: this class shows how to avoid stringly typed distributed contracts in an
 * event-driven system.
 *
 * <p>Bug prevented: enum constants eliminate typo drift between producers and consumers, which is
 * especially important when downstream services trigger notifications or compensation logic based
 * on an error code.
 */
public enum ErrorCode {
  RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "Resource not found"),
  DUPLICATE_RESOURCE(HttpStatus.CONFLICT, "Resource already exists"),
  INSUFFICIENT_FUNDS(HttpStatus.UNPROCESSABLE_ENTITY, "Insufficient account balance"),
  UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Authentication required"),
  FORBIDDEN(HttpStatus.FORBIDDEN, "Access denied"),
  VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "Request validation failed"),
  ACCOUNT_LOCKED(HttpStatus.LOCKED, "Account temporarily locked"),
  ACCOUNT_FROZEN(HttpStatus.UNPROCESSABLE_ENTITY, "Account is frozen"),
  IDEMPOTENCY_CONFLICT(HttpStatus.CONFLICT, "Duplicate request detected"),
  SAGA_COMPENSATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "Transaction reversal failed"),
  INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error");

  private final HttpStatus httpStatus;
  private final String defaultMessage;

  ErrorCode(HttpStatus httpStatus, String defaultMessage) {
    this.httpStatus = httpStatus;
    this.defaultMessage = defaultMessage;
  }

  public HttpStatus getHttpStatus() {
    return httpStatus;
  }

  public String getDefaultMessage() {
    return defaultMessage;
  }
}
