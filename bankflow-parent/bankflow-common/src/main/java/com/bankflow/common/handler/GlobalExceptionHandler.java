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
import com.bankflow.common.exception.ValidationException;
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

  @ExceptionHandler(ResourceNotFoundException.class)
  public ResponseEntity<ApiResponse<Void>> handleResourceNotFound(ResourceNotFoundException ex) {
    return buildErrorResponse(ErrorCode.RESOURCE_NOT_FOUND, ex.getMessage());
  }

  @ExceptionHandler(DuplicateResourceException.class)
  public ResponseEntity<ApiResponse<Void>> handleDuplicateResource(DuplicateResourceException ex) {
    return buildErrorResponse(ErrorCode.DUPLICATE_RESOURCE, ex.getMessage());
  }

  @ExceptionHandler(UnauthorizedException.class)
  public ResponseEntity<ApiResponse<Void>> handleUnauthorized(UnauthorizedException ex) {
    return buildErrorResponse(ErrorCode.UNAUTHORIZED, ex.getMessage());
  }

  @ExceptionHandler(InsufficientFundsException.class)
  public ResponseEntity<ApiResponse<Void>> handleInsufficientFunds(InsufficientFundsException ex) {
    return buildErrorResponse(ErrorCode.INSUFFICIENT_FUNDS, ex.getMessage());
  }

  @ExceptionHandler(AccountFrozenException.class)
  public ResponseEntity<ApiResponse<Void>> handleAccountFrozen(AccountFrozenException ex) {
    return buildErrorResponse(ErrorCode.ACCOUNT_FROZEN, ex.getMessage());
  }

  @ExceptionHandler(AccountLockedException.class)
  public ResponseEntity<ApiResponse<Void>> handleAccountLocked(AccountLockedException ex) {
    return buildErrorResponse(ErrorCode.ACCOUNT_LOCKED, ex.getMessage());
  }

  @ExceptionHandler(IdempotencyConflictException.class)
  public ResponseEntity<ApiResponse<Void>> handleIdempotencyConflict(IdempotencyConflictException ex) {
    return buildErrorResponse(ErrorCode.IDEMPOTENCY_CONFLICT, ex.getMessage());
  }

  /**
   * Maps business-rule validation failures to HTTP 400.
   *
   * <p>Plain English: this covers invalid transfer semantics such as negative amounts or attempting
   * to transfer money to the same account.
   */
  @ExceptionHandler(ValidationException.class)
  public ResponseEntity<ApiResponse<Void>> handleValidationException(ValidationException ex) {
    return buildErrorResponse(ErrorCode.VALIDATION_ERROR, ex.getMessage());
  }

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

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception ex) {
    log.error("Unhandled exception caught by GlobalExceptionHandler", ex);
    return buildErrorResponse(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.getDefaultMessage());
  }

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
