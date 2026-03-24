package com.bankflow.gateway.controller;

import com.bankflow.common.api.ApiResponse;
import com.bankflow.common.error.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reactive fallback endpoints used by circuit breakers when downstream services are unavailable.
 *
 * <p>Plain English: when a service is down or the circuit is open, the gateway returns a clear 503
 * response immediately instead of hanging or leaking internal exceptions to clients.
 */
@RestController
public class FallbackController {

  @GetMapping("/fallback/account")
  public ResponseEntity<ApiResponse<Void>> accountFallback() {
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
        .body(ApiResponse.error(
            "Account service temporarily unavailable. Please retry.",
            ErrorCode.SERVICE_UNAVAILABLE.name()));
  }

  @GetMapping("/fallback/payment")
  public ResponseEntity<ApiResponse<Void>> paymentFallback() {
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
        .body(ApiResponse.error(
            "Payment service temporarily unavailable. Please retry.",
            ErrorCode.SERVICE_UNAVAILABLE.name()));
  }

  @GetMapping("/fallback/auth")
  public ResponseEntity<ApiResponse<Void>> authFallback() {
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
        .body(ApiResponse.error(
            "Auth service temporarily unavailable. Please retry.",
            ErrorCode.SERVICE_UNAVAILABLE.name()));
  }
}
