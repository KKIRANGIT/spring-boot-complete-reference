package com.bankflow.account.controller;

import com.bankflow.account.dto.AccountBalanceResponse;
import com.bankflow.account.dto.AccountResponse;
import com.bankflow.account.dto.AccountStatementEntryResponse;
import com.bankflow.account.dto.CreateAccountRequest;
import com.bankflow.account.dto.UpdateAccountStatusRequest;
import com.bankflow.account.service.AccountCommandService;
import com.bankflow.account.service.AccountQueryService;
import com.bankflow.account.service.command.CreateAccountCommand;
import com.bankflow.account.service.command.UpdateAccountStatusCommand;
import com.bankflow.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/accounts")
@Tag(name = "Accounts", description = "Account creation, balance reads, statements, and admin status updates.")
public class AccountController {

  private final AccountCommandService accountCommandService;
  private final AccountQueryService accountQueryService;

  public AccountController(
      AccountCommandService accountCommandService,
      AccountQueryService accountQueryService) {
    this.accountCommandService = accountCommandService;
    this.accountQueryService = accountQueryService;
  }

  @PostMapping
  @PreAuthorize("isAuthenticated()")
  @SecurityRequirement(name = "gatewayHeaders")
  @Operation(summary = "Create account", description = "Creates a new bank account for the user id stamped by the gateway.")
  @ApiResponses({
      @io.swagger.v3.oas.annotations.responses.ApiResponse(
          responseCode = "201",
          description = "Account created successfully",
          content = @Content(schema = @Schema(implementation = ApiResponse.class))),
      @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation failed"),
      @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Gateway identity missing or invalid"),
      @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "423", description = "Account is temporarily locked"),
      @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Internal server error")
  })
  public ResponseEntity<ApiResponse<AccountResponse>> createAccount(
      @RequestHeader("X-User-Id") UUID userId,
      @Valid @RequestBody CreateAccountRequest request) {
    AccountResponse response = accountCommandService.createAccount(new CreateAccountCommand(
        userId,
        request.accountType(),
        request.initialDeposit(),
        request.currency(),
        userId));
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success("Account created successfully", response));
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN') or @accountSecurityService.isOwner(authentication, #id)")
  @SecurityRequirement(name = "gatewayHeaders")
  @Operation(summary = "Get account by id", description = "Returns account details for the owner or an admin.")
  public ResponseEntity<ApiResponse<AccountResponse>> getAccountById(@PathVariable UUID id) {
    return ResponseEntity.ok(ApiResponse.success("Account fetched successfully", accountQueryService.getAccountById(id)));
  }

  @GetMapping("/my")
  @PreAuthorize("isAuthenticated()")
  @SecurityRequirement(name = "gatewayHeaders")
  @Operation(summary = "Get my accounts", description = "Returns all accounts that belong to the gateway-authenticated user.")
  public ResponseEntity<ApiResponse<List<AccountResponse>>> getMyAccounts(@RequestHeader("X-User-Id") UUID userId) {
    return ResponseEntity.ok(ApiResponse.success("Accounts fetched successfully", accountQueryService.getAccountsByUserId(userId)));
  }

  @GetMapping("/{accountId}/balance")
  @PreAuthorize("hasRole('ADMIN') or @accountSecurityService.isOwner(authentication, #accountId)")
  @SecurityRequirement(name = "gatewayHeaders")
  @Operation(summary = "Get account balance", description = "Returns the current balance using the short-lived balance cache.")
  public ResponseEntity<ApiResponse<AccountBalanceResponse>> getBalance(@PathVariable UUID accountId) {
    return ResponseEntity.ok(ApiResponse.success("Balance fetched successfully", accountQueryService.getBalance(accountId)));
  }

  @GetMapping("/{accountId}/statement")
  @PreAuthorize("hasRole('ADMIN') or @accountSecurityService.isOwner(authentication, #accountId)")
  @SecurityRequirement(name = "gatewayHeaders")
  @Operation(summary = "Get account statement", description = "Returns a paginated statement from the audit-log table.")
  public ResponseEntity<ApiResponse<Page<AccountStatementEntryResponse>>> getStatement(
      @PathVariable UUID accountId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    Pageable pageable = PageRequest.of(page, size);
    return ResponseEntity.ok(ApiResponse.success("Statement fetched successfully", accountQueryService.getAccountStatement(accountId, pageable)));
  }

  @PatchMapping("/{id}/status")
  @PreAuthorize("hasRole('ADMIN')")
  @SecurityRequirement(name = "gatewayHeaders")
  @Operation(summary = "Update account status", description = "Changes the lifecycle state of an account.")
  public ResponseEntity<ApiResponse<AccountResponse>> updateStatus(
      @PathVariable UUID id,
      @RequestHeader("X-User-Id") UUID performedBy,
      @Valid @RequestBody UpdateAccountStatusRequest request) {
    AccountResponse response = accountCommandService.updateAccountStatus(new UpdateAccountStatusCommand(
        id,
        request.status(),
        performedBy,
        request.description()));
    return ResponseEntity.ok(ApiResponse.success("Account status updated successfully", response));
  }
}
