package com.bankflow.gateway.controller;

import com.bankflow.common.api.ApiResponse;
import com.bankflow.common.error.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reactive fallback endpoints used by circuit breakers when downstream services are unavailable.
 *
 * <p>Plain English: when a service is down or the circuit is open, the gateway returns a clear 503
 * response immediately instead of hanging or leaking internal exceptions to clients.
 */
@RestController
@Tag(name = "Gateway Fallbacks", description = "Circuit-breaker fallback responses returned by the API gateway when downstream services are unavailable.")
public class FallbackController {

  @RequestMapping("/fallback/account")
  @Operation(summary = "Account service fallback", description = "Returns a 503 response when the account-service circuit breaker is open or downstream calls fail fast.")
  @ApiResponses({
      @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503", description = "Account service temporarily unavailable")
  })
  public ResponseEntity<ApiResponse<Void>> accountFallback() {
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
        .body(ApiResponse.error(
            "Account service temporarily unavailable. Please retry.",
            ErrorCode.SERVICE_UNAVAILABLE.name()));
  }

  @RequestMapping("/fallback/payment")
  @Operation(summary = "Payment service fallback", description = "Returns a 503 response when the payment-service circuit breaker is open or downstream calls fail fast.")
  @ApiResponses({
      @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503", description = "Payment service temporarily unavailable")
  })
  public ResponseEntity<ApiResponse<Void>> paymentFallback() {
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
        .body(ApiResponse.error(
            "Payment service temporarily unavailable. Please retry.",
            ErrorCode.SERVICE_UNAVAILABLE.name()));
  }

  @RequestMapping("/fallback/auth")
  @Operation(summary = "Auth service fallback", description = "Returns a 503 response when the auth-service circuit breaker is open or downstream calls fail fast.")
  @ApiResponses({
      @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503", description = "Auth service temporarily unavailable")
  })
  public ResponseEntity<ApiResponse<Void>> authFallback() {
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
        .body(ApiResponse.error(
            "Auth service temporarily unavailable. Please retry.",
            ErrorCode.SERVICE_UNAVAILABLE.name()));
  }
}
