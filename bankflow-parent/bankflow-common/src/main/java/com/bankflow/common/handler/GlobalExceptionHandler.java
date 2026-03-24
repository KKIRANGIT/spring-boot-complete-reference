package com.bankflow.common.handler;

import com.bankflow.common.api.ApiResponse;
import com.bankflow.common.error.ErrorCode;
import com.bankflow.common.exception.AccountFrozenException;
import com.bankflow.common.exception.AccountLockedException;
import com.bankflow.common.exception.DuplicateResourceException;
import com.bankflow.common.exception.IdempotencyConflictException;
import com.bankflow.common.exception.InsufficientFundsException;
import com.bankflow.common.exception.ResourceNotFoundException;
import com.bankflow.common.exception.UnauthorizedException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Centralized exception-to-response mapper for BankFlow REST APIs.
 *
 * <p>Design decision: every service shares one exception policy so HTTP status codes, error codes,
 * and payload shape stay consistent across the entire platform.
 *
 * <p>Interview answer: this class demonstrates how to build a secure, predictable REST error
 * contract in a distributed system.
 *
 * <p>Security issue prevented: raw exception messages and stack traces are never exposed to API
 * callers, because leaking class names and internals in a banking API gives attackers unnecessary
 * insight into the system.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  /**
   * Maps resource-missing cases to HTTP 404.
   *
   * <p>Plain English: when a requested record does not exist, callers receive a not-found response
   * instead of a generic server error.
   *
   * <p>Interview question answered: "How do you make domain exceptions map to correct HTTP
   * semantics?"
   */
  @ExceptionHandler(ResourceNotFoundException.class)
  public ResponseEntity<ApiResponse<Void>> handleResourceNotFound(ResourceNotFoundException ex) {
    return buildErrorResponse(ErrorCode.RESOURCE_NOT_FOUND, ex.getMessage());
  }

  /**
   * Maps duplicate-creation attempts to HTTP 409.
   */
  @ExceptionHandler(DuplicateResourceException.class)
  public ResponseEntity<ApiResponse<Void>> handleDuplicateResource(DuplicateResourceException ex) {
    return buildErrorResponse(ErrorCode.DUPLICATE_RESOURCE, ex.getMessage());
  }

  /**
   * Maps authentication failures to HTTP 401.
   */
  @ExceptionHandler(UnauthorizedException.class)
  public ResponseEntity<ApiResponse<Void>> handleUnauthorized(UnauthorizedException ex) {
    return buildErrorResponse(ErrorCode.UNAUTHORIZED, ex.getMessage());
  }

  /**
   * Maps balance failures to HTTP 422.
   */
  @ExceptionHandler(InsufficientFundsException.class)
  public ResponseEntity<ApiResponse<Void>> handleInsufficientFunds(InsufficientFundsException ex) {
    return buildErrorResponse(ErrorCode.INSUFFICIENT_FUNDS, ex.getMessage());
  }

  /**
   * Maps frozen-account actions to HTTP 422.
   */
  @ExceptionHandler(AccountFrozenException.class)
  public ResponseEntity<ApiResponse<Void>> handleAccountFrozen(AccountFrozenException ex) {
    return buildErrorResponse(ErrorCode.ACCOUNT_FROZEN, ex.getMessage());
  }

  /**
   * Maps temporary lockouts to HTTP 423.
   */
  @ExceptionHandler(AccountLockedException.class)
  public ResponseEntity<ApiResponse<Void>> handleAccountLocked(AccountLockedException ex) {
    return buildErrorResponse(ErrorCode.ACCOUNT_LOCKED, ex.getMessage());
  }

  /**
   * Maps duplicate idempotency keys to HTTP 409.
   */
  @ExceptionHandler(IdempotencyConflictException.class)
  public ResponseEntity<ApiResponse<Void>> handleIdempotencyConflict(IdempotencyConflictException ex) {
    return buildErrorResponse(ErrorCode.IDEMPOTENCY_CONFLICT, ex.getMessage());
  }

  /**
   * Maps bean validation failures to a field -> message map.
   *
   * <p>Plain English: callers learn exactly which request field failed and why.
   *
   * <p>Design decision: field-level feedback is more useful than one generic validation string,
   * especially for frontend forms and mobile clients.
   *
   * <p>Bug prevented: vague validation errors lead to repeated bad retries and poor user guidance.
   */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiResponse<Map<String, String>>> handleValidationFailure(
      MethodArgumentNotValidException ex) {
    Map<String, String> fieldErrors = new LinkedHashMap<>();

    for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
      fieldErrors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
    }

    ApiResponse<Map<String, String>> response = new ApiResponse<>(
        false,
        ErrorCode.VALIDATION_ERROR.getDefaultMessage(),
        fieldErrors,
        ErrorCode.VALIDATION_ERROR.name(),
        LocalDateTime.now());

    return ResponseEntity.status(ErrorCode.VALIDATION_ERROR.getHttpStatus()).body(response);
  }

  /**
   * Catches unexpected failures and returns a safe generic error.
   *
   * <p>Plain English: clients never see stack traces or internal exception details.
   *
   * <p>Security issue prevented: leaking framework classes, SQL messages, or stack traces helps
   * attackers fingerprint the system.
   *
   * <p>Interview question answered: "How do you avoid leaking internals from a banking API?"
   */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception ex) {
    log.error("Unhandled exception caught by GlobalExceptionHandler", ex);
    return buildErrorResponse(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.getDefaultMessage());
  }

  /**
   * Builds the shared BankFlow error envelope.
   */
  private ResponseEntity<ApiResponse<Void>> buildErrorResponse(ErrorCode errorCode, String message) {
    ApiResponse<Void> response = new ApiResponse<>(
        false,
        message,
        null,
        errorCode.name(),
        LocalDateTime.now());
    return ResponseEntity.status(errorCode.getHttpStatus()).body(response);
  }
}
