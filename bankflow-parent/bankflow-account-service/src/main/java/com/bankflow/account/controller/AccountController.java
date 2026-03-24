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

/**
 * REST endpoints for account creation, reads, and admin status changes.
 *
 * <p>Plain English: these APIs expose the public account-service surface while delegating all real
 * work to the CQRS command and query services.
 *
 * <p>Design decision: the gateway validates JWTs and stamps user identity headers, so this service
 * focuses on authorization and business rules instead of internet-facing token parsing.
 *
 * <p>Interview question answered: "How do downstream microservices enforce ownership and admin
 * rules when the API gateway already authenticated the request?"
 */
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

  /**
   * Creates a new account for the authenticated user.
   *
   * <p>Plain English: the gateway tells this service which user is calling, and the write side
   * creates the account plus the initial audit record in one transaction.
   *
   * <p>Bug prevented: taking user ownership from the body instead of the trusted header would let a
   * caller try to create accounts on behalf of another user.
   */
  @PostMapping
  @PreAuthorize("isAuthenticated()")
  @SecurityRequirement(name = "gatewayHeaders")
  @Operation(summary = "Create account", description = "Creates a new bank account for the user id stamped by the gateway.")
  @ApiResponses({
      @io.swagger.v3.oas.annotations.responses.ApiResponse(
          responseCode = "201",
          description = "Account created successfully",
          content = @Content(schema = @Schema(implementation = ApiResponse.class))),
      @io.swagger.v3.oas.annotations.responses.ApiResponse(
          responseCode = "400",
          description = "Validation failed"),
      @io.swagger.v3.oas.annotations.responses.ApiResponse(
          responseCode = "401",
          description = "Gateway identity missing or invalid"),
      @io.swagger.v3.oas.annotations.responses.ApiResponse(
          responseCode = "423",
          description = "Account is temporarily locked"),
      @io.swagger.v3.oas.annotations.responses.ApiResponse(
          responseCode = "500",
          description = "Internal server error")
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

  /**
   * Returns one account by id if the caller owns it or is an admin.
   *
   * <p>Plain English: this enforces the core banking rule that users can only read their own
   * accounts unless they have an elevated internal role.
   *
   * <p>Security issue prevented: without the ownership check, any authenticated user could fetch
   * another customer's account details by guessing UUIDs.
   */
  @GetMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN') or @accountSecurity.isOwner(authentication, #id)")
  @SecurityRequirement(name = "gatewayHeaders")
  @Operation(summary = "Get account by id", description = "Returns account details for the owner or an admin.")
  @ApiResponses({
      @io.swagger.v3.oas.annotations.responses.ApiResponse(
          responseCode = "200",
          description = "Account returned successfully",
          content = @Content(schema = @Schema(implementation = ApiResponse.class))),
      @io.swagger.v3.oas.annotations.responses.ApiResponse(
          responseCode = "400",
          description = "Invalid account id"),
      @io.swagger.v3.oas.annotations.responses.ApiResponse(
          responseCode = "401",
          description = "Gateway identity missing or invalid"),
      @io.swagger.v3.oas.annotations.responses.ApiResponse(
          responseCode = "423",
          description = "Account is temporarily locked"),
      @io.swagger.v3.oas.annotations.responses.ApiResponse(
          responseCode = "500",
          description = "Internal server error")
  })
  public ResponseEntity<ApiResponse<AccountResponse>> getAccountById(@PathVariable UUID id) {
    return ResponseEntity.ok(ApiResponse.success(
        "Account fetched successfully",
        accountQueryService.getAccountById(id)));
  }

  /**
   * Returns all accounts for the gateway-authenticated user.
   *
   * <p>Plain English: this is the "show me all my accounts" endpoint that the account overview page
   * would call after gateway authentication succeeds.
   */
  @GetMapping("/my")
  @PreAuthorize("isAuthenticated()")
  @SecurityRequirement(name = "gatewayHeaders")
  @Operation(summary = "Get my accounts", description = "Returns all accounts that belong to the gateway-authenticated user.")
  @ApiResponses({
      @io.swagger.v3.oas.annotations.responses.ApiResponse(
          responseCode = "200",
          description = "Accounts returned successfully",
          content = @Content(schema = @Schema(implementation = ApiResponse.class))),
      @io.swagger.v3.oas.annotations.responses.ApiResponse(
          responseCode = "400",
          description = "Invalid request"),
      @io.swagger.v3.oas.annotations.responses.ApiResponse(
          responseCode = "401",
          description = "Gateway identity missing or invalid"),
      @io.swagger.v3.oas.annotations.responses.ApiResponse(
          responseCode = "423",
          description = "Account is temporarily locked"),
      @io.swagger.v3.oas.annotations.responses.ApiResponse(
          responseCode = "500",
          description = "Internal server error")
  })
  public ResponseEntity<ApiResponse<List<AccountResponse>>> getMyAccounts(
      @RequestHeader("X-User-Id") UUID userId) {
    return ResponseEntity.ok(ApiResponse.success(
        "Accounts fetched successfully",
        accountQueryService.getAccountsByUserId(userId)));
  }

  /**
   * Returns the current balance snapshot for one account.
   *
   * <p>Plain English: this read path is optimized for high-frequency balance checks and reads from
   * the dedicated short-lived balance cache before falling back to MySQL.
   */
  @GetMapping("/{id}/balance")
  @PreAuthorize("hasRole('ADMIN') or @accountSecurity.isOwner(authentication, #id)")
  @SecurityRequirement(name = "gatewayHeaders")
  @Operation(summary = "Get account balance", description = "Returns the current balance using the short-lived balance cache.")
  @ApiResponses({
      @io.swagger.v3.oas.annotations.responses.ApiResponse(
          responseCode = "200",
          description = "Balance returned successfully",
          content = @Content(schema = @Schema(implementation = ApiResponse.class))),
      @io.swagger.v3.oas.annotations.responses.ApiResponse(
          responseCode = "400",
          description = "Invalid account id"),
      @io.swagger.v3.oas.annotations.responses.ApiResponse(
          responseCode = "401",
          description = "Gateway identity missing or invalid"),
      @io.swagger.v3.oas.annotations.responses.ApiResponse(
          responseCode = "423",
          description = "Account is temporarily locked"),
      @io.swagger.v3.oas.annotations.responses.ApiResponse(
          responseCode = "500",
          description = "Internal server error")
  })
  public ResponseEntity<ApiResponse<AccountBalanceResponse>> getBalance(@PathVariable UUID id) {
    return ResponseEntity.ok(ApiResponse.success(
        "Balance fetched successfully",
        accountQueryService.getBalance(id)));
  }

  /**
   * Returns the latest audit log entries for one account.
   *
   * <p>Plain English: statements always hit the audit table directly so customers and regulators do
   * not see stale transaction history from a cache.
   */
  @GetMapping("/{id}/statement")
  @PreAuthorize("hasRole('ADMIN') or @accountSecurity.isOwner(authentication, #id)")
  @SecurityRequirement(name = "gatewayHeaders")
  @Operation(summary = "Get account statement", description = "Returns a paginated statement from the audit-log table.")
  @ApiResponses({
      @io.swagger.v3.oas.annotations.responses.ApiResponse(
          responseCode = "200",
          description = "Statement returned successfully",
          content = @Content(schema = @Schema(implementation = ApiResponse.class))),
      @io.swagger.v3.oas.annotations.responses.ApiResponse(
          responseCode = "400",
          description = "Invalid request"),
      @io.swagger.v3.oas.annotations.responses.ApiResponse(
          responseCode = "401",
          description = "Gateway identity missing or invalid"),
      @io.swagger.v3.oas.annotations.responses.ApiResponse(
          responseCode = "423",
          description = "Account is temporarily locked"),
      @io.swagger.v3.oas.annotations.responses.ApiResponse(
          responseCode = "500",
          description = "Internal server error")
  })
  public ResponseEntity<ApiResponse<Page<AccountStatementEntryResponse>>> getStatement(
      @PathVariable UUID id,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    Pageable pageable = PageRequest.of(page, size);
    return ResponseEntity.ok(ApiResponse.success(
        "Statement fetched successfully",
        accountQueryService.getAccountStatement(id, pageable)));
  }

  /**
   * Changes account status and is restricted to administrators.
   *
   * <p>Plain English: this supports operational actions like freezing or closing an account while
   * still writing a full audit trail.
   */
  @PatchMapping("/{id}/status")
  @PreAuthorize("hasRole('ADMIN')")
  @SecurityRequirement(name = "gatewayHeaders")
  @Operation(summary = "Update account status", description = "Changes the lifecycle state of an account.")
  @ApiResponses({
      @io.swagger.v3.oas.annotations.responses.ApiResponse(
          responseCode = "200",
          description = "Status updated successfully",
          content = @Content(schema = @Schema(implementation = ApiResponse.class))),
      @io.swagger.v3.oas.annotations.responses.ApiResponse(
          responseCode = "400",
          description = "Validation failed"),
      @io.swagger.v3.oas.annotations.responses.ApiResponse(
          responseCode = "401",
          description = "Gateway identity missing or invalid"),
      @io.swagger.v3.oas.annotations.responses.ApiResponse(
          responseCode = "423",
          description = "Account is temporarily locked"),
      @io.swagger.v3.oas.annotations.responses.ApiResponse(
          responseCode = "500",
          description = "Internal server error")
  })
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
