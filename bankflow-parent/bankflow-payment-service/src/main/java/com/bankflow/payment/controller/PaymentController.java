package com.bankflow.payment.controller;

import com.bankflow.common.api.ApiResponse;
import com.bankflow.payment.dto.TransactionResponse;
import com.bankflow.payment.dto.TransferRequest;
import com.bankflow.payment.dto.TransferResponse;
import com.bankflow.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoints for payment initiation and payment-status lookups.
 */
@RestController
@RequestMapping("/api/v1/payments")
@Tag(name = "Payments", description = "Transfer initiation, saga orchestration, and payment lookups.")
public class PaymentController {

  private final PaymentService paymentService;

  public PaymentController(PaymentService paymentService) {
    this.paymentService = paymentService;
  }

  @PostMapping("/transfers")
  @PreAuthorize("isAuthenticated()")
  @SecurityRequirement(name = "gatewayHeaders")
  @Operation(
      summary = "Initiate transfer",
      description = "Creates a pending transfer, stores its outbox row in the same database transaction, and returns an idempotent pending response.")
  @ApiResponses({
      @io.swagger.v3.oas.annotations.responses.ApiResponse(
          responseCode = "202",
          description = "Transfer accepted and saga started"),
      @io.swagger.v3.oas.annotations.responses.ApiResponse(
          responseCode = "400",
          description = "Validation failed"),
      @io.swagger.v3.oas.annotations.responses.ApiResponse(
          responseCode = "500",
          description = "Internal server error")
  })
  public ResponseEntity<ApiResponse<TransferResponse>> initiateTransfer(
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @Valid @RequestBody TransferRequest request) {
    TransferResponse response = paymentService.initiateTransfer(request, idempotencyKey);
    return ResponseEntity.status(HttpStatus.ACCEPTED)
        .body(ApiResponse.success("Transfer accepted successfully", response));
  }

  @GetMapping("/{transactionId}")
  @PreAuthorize("isAuthenticated()")
  @SecurityRequirement(name = "gatewayHeaders")
  @Operation(summary = "Get transaction", description = "Returns the current persisted state of one payment transaction.")
  public ResponseEntity<ApiResponse<TransactionResponse>> getTransaction(@PathVariable UUID transactionId) {
    return ResponseEntity.ok(ApiResponse.success(
        "Transaction fetched successfully",
        paymentService.getTransaction(transactionId)));
  }

  @GetMapping("/reference/{transactionReference}")
  @PreAuthorize("isAuthenticated()")
  @SecurityRequirement(name = "gatewayHeaders")
  @Operation(summary = "Get transaction by reference", description = "Returns the persisted transaction using the user-facing payment reference.")
  public ResponseEntity<ApiResponse<TransactionResponse>> getTransactionByReference(
      @PathVariable String transactionReference) {
    return ResponseEntity.ok(ApiResponse.success(
        "Transaction fetched successfully",
        paymentService.getTransactionByReference(transactionReference)));
  }
}
