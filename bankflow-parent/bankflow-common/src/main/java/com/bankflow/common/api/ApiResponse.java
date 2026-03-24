package com.bankflow.common.api;

import com.bankflow.common.error.ErrorCode;
import java.time.LocalDateTime;

/**
 * Standard response wrapper used by every BankFlow HTTP API.
 *
 * <p>Design decision: every service returns the same envelope so the frontend can always read
 * the payload, timestamp, and error details from a predictable location without special-casing
 * one microservice versus another.
 *
 * <p>Interview answer: this class demonstrates how to standardize contracts across multiple
 * teams so frontend and mobile clients never guess where data or errors will appear.
 *
 * <p>Bug/security issue prevented: inconsistent ad hoc responses create fragile clients and make
 * it easy to leak raw exception payloads by mistake. This wrapper keeps both success and failure
 * paths explicit.
 *
 * @param <T> response payload type
 */
public class ApiResponse<T> {

  private boolean success;
  private String message;
  private T data;
  private String errorCode;
  private LocalDateTime timestamp;

  public ApiResponse() {
    this.timestamp = LocalDateTime.now();
  }

  public ApiResponse(boolean success, String message, T data, String errorCode, LocalDateTime timestamp) {
    this.success = success;
    this.message = message;
    this.data = data;
    this.errorCode = errorCode;
    this.timestamp = timestamp;
  }

  /**
   * Builds a success envelope with a default success message.
   */
  public static <T> ApiResponse<T> success(T data) {
    return success("Request completed successfully", data);
  }

  /**
   * Builds a success envelope with an explicit message and payload.
   */
  public static <T> ApiResponse<T> success(String message, T data) {
    return new ApiResponse<>(true, message, data, null, LocalDateTime.now());
  }

  /**
   * Builds an error envelope from explicit message and code values.
   */
  public static <T> ApiResponse<T> error(String message, String errorCode) {
    return new ApiResponse<>(false, message, null, errorCode, LocalDateTime.now());
  }

  /**
   * Builds an error envelope from the shared enum so every service emits the same code.
   */
  public static <T> ApiResponse<T> error(ErrorCode errorCode) {
    return error(errorCode.getDefaultMessage(), errorCode.name());
  }

  public boolean isSuccess() {
    return success;
  }

  public void setSuccess(boolean success) {
    this.success = success;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  public T getData() {
    return data;
  }

  public void setData(T data) {
    this.data = data;
  }

  public String getErrorCode() {
    return errorCode;
  }

  public void setErrorCode(String errorCode) {
    this.errorCode = errorCode;
  }

  public LocalDateTime getTimestamp() {
    return timestamp;
  }

  public void setTimestamp(LocalDateTime timestamp) {
    this.timestamp = timestamp;
  }
}
