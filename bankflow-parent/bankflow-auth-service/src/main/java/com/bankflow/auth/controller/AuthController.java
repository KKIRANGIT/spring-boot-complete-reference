package com.bankflow.auth.controller;

import com.bankflow.auth.dto.LoginRequest;
import com.bankflow.auth.dto.LoginResponse;
import com.bankflow.auth.dto.RefreshTokenRequest;
import com.bankflow.auth.dto.RegisterRequest;
import com.bankflow.auth.dto.RegisterResponse;
import com.bankflow.auth.dto.UserProfileResponse;
import com.bankflow.auth.security.CustomUserDetails;
import com.bankflow.auth.service.AuthService;
import com.bankflow.common.api.ApiResponse;
import com.bankflow.common.exception.UnauthorizedException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoints for authentication flows.
 *
 * <p>Plain English: this controller exposes registration, login, refresh, logout, and profile APIs
 * under {@code /api/v1/auth}.
 *
 * <p>Design decision: all responses use the shared {@link ApiResponse} envelope so frontend clients
 * always receive the same top-level structure regardless of endpoint outcome.
 *
 * <p>Interview question answered: "How do you design a clean auth controller for a banking API?"
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Registration, login, token rotation, logout, and user profile operations.")
public class AuthController {

  private final AuthService authService;

  /** Registers a new user account. */
  @PostMapping("/register")
  @Operation(summary = "Register a new user", description = "Creates a new BankFlow auth user with ROLE_USER and a BCrypt-hashed password.")
  @io.swagger.v3.oas.annotations.responses.ApiResponses(value = {
      @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "User registered successfully"),
      @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid registration request"),
      @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
      @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "423", description = "Account locked"),
      @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Internal server error")
  })
  public ResponseEntity<ApiResponse<RegisterResponse>> register(@Valid @RequestBody RegisterRequest request) {
    RegisterResponse response = authService.register(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("User registered successfully", response));
  }

  /** Authenticates a user and returns JWT plus refresh token. */
  @PostMapping("/login")
  @Operation(summary = "Authenticate a user", description = "Validates credentials, applies lockout rules, and issues a JWT access token plus a rotating refresh token.")
  @io.swagger.v3.oas.annotations.responses.ApiResponses(value = {
      @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Login successful"),
      @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid login request"),
      @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Invalid credentials or inactive account"),
      @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "423", description = "Account temporarily locked"),
      @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Internal server error")
  })
  public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
    LoginResponse response = authService.login(request);
    return ResponseEntity.ok(ApiResponse.success("Login successful", response));
  }

  /** Rotates a refresh token and issues a fresh token pair. */
  @PostMapping("/refresh-token")
  @Operation(summary = "Rotate refresh token", description = "Revokes the submitted refresh token, issues a new refresh token, and returns a new access token.")
  @io.swagger.v3.oas.annotations.responses.ApiResponses(value = {
      @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Token rotated successfully"),
      @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid refresh-token request"),
      @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Refresh token invalid, expired, or revoked"),
      @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "423", description = "Account temporarily locked"),
      @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Internal server error")
  })
  public ResponseEntity<ApiResponse<LoginResponse>> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
    LoginResponse response = authService.refreshToken(request);
    return ResponseEntity.ok(ApiResponse.success("Token refreshed successfully", response));
  }

  /** Logs the current device out by blacklisting the presented access token. */
  @PostMapping("/logout")
  @SecurityRequirement(name = "bearerAuth")
  @Operation(summary = "Logout current device", description = "Blacklists the current access token so it cannot be used again before expiry.")
  @io.swagger.v3.oas.annotations.responses.ApiResponses(value = {
      @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Logout successful"),
      @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Malformed bearer token"),
      @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing or invalid JWT"),
      @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "423", description = "Account temporarily locked"),
      @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Internal server error")
  })
  public ResponseEntity<ApiResponse<Void>> logout(
      @RequestHeader(HttpHeaders.AUTHORIZATION) String authorizationHeader,
      @AuthenticationPrincipal CustomUserDetails principal) {
    authService.logout(extractBearerToken(authorizationHeader), principal.getId());
    return ResponseEntity.ok(ApiResponse.success("Logout successful", null));
  }

  /** Logs the user out from every device. */
  @PostMapping("/logout-all-devices")
  @SecurityRequirement(name = "bearerAuth")
  @Operation(summary = "Logout all devices", description = "Revokes all refresh tokens for the user and blacklists the current access token.")
  @io.swagger.v3.oas.annotations.responses.ApiResponses(value = {
      @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "All sessions revoked successfully"),
      @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Malformed bearer token"),
      @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing or invalid JWT"),
      @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "423", description = "Account temporarily locked"),
      @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Internal server error")
  })
  public ResponseEntity<ApiResponse<Void>> logoutAllDevices(
      @RequestHeader(HttpHeaders.AUTHORIZATION) String authorizationHeader,
      @AuthenticationPrincipal CustomUserDetails principal) {
    authService.logoutAllDevices(extractBearerToken(authorizationHeader), principal.getId());
    return ResponseEntity.ok(ApiResponse.success("All sessions revoked successfully", null));
  }

  /** Returns the authenticated user's profile. */
  @GetMapping("/me")
  @SecurityRequirement(name = "bearerAuth")
  @Operation(summary = "Get current user profile", description = "Returns the basic identity profile for the currently authenticated user.")
  @io.swagger.v3.oas.annotations.responses.ApiResponses(value = {
      @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Profile returned successfully"),
      @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request"),
      @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing or invalid JWT"),
      @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "423", description = "Account temporarily locked"),
      @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Internal server error")
  })
  public ResponseEntity<ApiResponse<UserProfileResponse>> me(@AuthenticationPrincipal CustomUserDetails principal) {
    UserProfileResponse response = authService.getCurrentUserProfile(principal.getId());
    return ResponseEntity.ok(ApiResponse.success("Profile fetched successfully", response));
  }

  /** Strips the Bearer prefix from the Authorization header. */
  private String extractBearerToken(String authorizationHeader) {
    if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
      throw new UnauthorizedException("Missing or invalid Authorization header");
    }
    return authorizationHeader.substring(7);
  }
}
