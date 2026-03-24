package com.bankflow.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bankflow.auth.dto.LoginRequest;
import com.bankflow.auth.dto.LoginResponse;
import com.bankflow.auth.dto.RefreshTokenRequest;
import com.bankflow.auth.dto.RegisterRequest;
import com.bankflow.auth.dto.RegisterResponse;
import com.bankflow.auth.dto.UserProfileResponse;
import com.bankflow.auth.entity.Role;
import com.bankflow.auth.entity.RoleName;
import com.bankflow.auth.entity.User;
import com.bankflow.auth.security.CustomUserDetails;
import com.bankflow.auth.service.AuthService;
import com.bankflow.common.api.ApiResponse;
import com.bankflow.common.exception.UnauthorizedException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Unit tests for {@link AuthController}.
 *
 * <p>Plain English: these tests verify the controller's HTTP-level contract without starting Spring
 * MVC, so response codes, wrappers, and bearer-header parsing stay stable.
 *
 * <p>Bug prevented: controller glue code is small but easy to overlook; a wrong status code or
 * malformed wrapper here breaks every client even when service logic is correct.
 */
@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

  @Mock
  private AuthService authService;

  @InjectMocks
  private AuthController authController;

  @Test
  @DisplayName("Register endpoint should return 201 with the shared API response envelope")
  void register_shouldReturnCreatedResponse() {
    // Arrange
    RegisterRequest request = new RegisterRequest("banker", "banker@bankflow.local", "Password1!");
    RegisterResponse registerResponse =
        new RegisterResponse(UUID.randomUUID(), "banker", "banker@bankflow.local");
    when(authService.register(request)).thenReturn(registerResponse);

    // Act
    ResponseEntity<ApiResponse<RegisterResponse>> response = authController.register(request);

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().isSuccess()).isTrue();
    assertThat(response.getBody().getData()).isEqualTo(registerResponse);
  }

  @Test
  @DisplayName("Login endpoint should return tokens inside the shared API response envelope")
  void login_shouldReturnOkResponse() {
    // Arrange
    LoginRequest request = new LoginRequest("banker", "Password1!");
    LoginResponse loginResponse = new LoginResponse(
        "access-token",
        "refresh-token",
        "Bearer",
        900L,
        UUID.randomUUID(),
        "banker",
        List.of("ROLE_USER"));
    when(authService.login(request)).thenReturn(loginResponse);

    // Act
    ResponseEntity<ApiResponse<LoginResponse>> response = authController.login(request);

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getData()).isEqualTo(loginResponse);
  }

  @Test
  @DisplayName("Refresh token endpoint should delegate to the service and return a new token pair")
  void refreshToken_shouldReturnOkResponse() {
    // Arrange
    RefreshTokenRequest request = new RefreshTokenRequest("refresh-token");
    LoginResponse loginResponse = new LoginResponse(
        "new-access-token",
        "new-refresh-token",
        "Bearer",
        900L,
        UUID.randomUUID(),
        "banker",
        List.of("ROLE_USER"));
    when(authService.refreshToken(request)).thenReturn(loginResponse);

    // Act
    ResponseEntity<ApiResponse<LoginResponse>> response = authController.refreshToken(request);

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getData()).isEqualTo(loginResponse);
  }

  @Test
  @DisplayName("Logout endpoint should strip the Bearer prefix and blacklist the current token")
  void logout_shouldBlacklistCurrentToken() {
    // Arrange
    CustomUserDetails principal = buildPrincipal(true, null);

    // Act
    ResponseEntity<ApiResponse<Void>> response =
        authController.logout("Bearer access-token", principal);

    // Assert
    verify(authService).logout("access-token", principal.getId());
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().isSuccess()).isTrue();
  }

  @Test
  @DisplayName("Logout all devices endpoint should revoke all sessions for the authenticated user")
  void logoutAllDevices_shouldDelegateToService() {
    // Arrange
    CustomUserDetails principal = buildPrincipal(true, null);

    // Act
    ResponseEntity<ApiResponse<Void>> response =
        authController.logoutAllDevices("Bearer access-token", principal);

    // Assert
    verify(authService).logoutAllDevices("access-token", principal.getId());
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().isSuccess()).isTrue();
  }

  @Test
  @DisplayName("Current user endpoint should return the profile of the authenticated principal")
  void me_shouldReturnCurrentUserProfile() {
    // Arrange
    CustomUserDetails principal = buildPrincipal(true, null);
    UserProfileResponse userProfileResponse = new UserProfileResponse(
        principal.getId(),
        principal.getUsername(),
        principal.getEmail(),
        List.of("ROLE_USER"),
        LocalDateTime.now().minusDays(7),
        LocalDateTime.now().minusMinutes(15));
    when(authService.getCurrentUserProfile(principal.getId())).thenReturn(userProfileResponse);

    // Act
    ResponseEntity<ApiResponse<UserProfileResponse>> response = authController.me(principal);

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getData()).isEqualTo(userProfileResponse);
  }

  @Test
  @DisplayName("Logout endpoints should reject malformed authorization headers")
  void logout_withMalformedAuthorizationHeader_shouldThrowUnauthorizedException() {
    // Arrange
    CustomUserDetails principal = buildPrincipal(true, null);

    // Act + Assert
    assertThatThrownBy(() -> authController.logout("Basic abc", principal))
        .isInstanceOf(UnauthorizedException.class)
        .hasMessageContaining("Missing or invalid Authorization header");
  }

  private CustomUserDetails buildPrincipal(boolean active, LocalDateTime lockedUntil) {
    Role role = new Role();
    role.setId(1L);
    role.setName(RoleName.ROLE_USER);

    User user = new User();
    user.setId(UUID.randomUUID());
    user.setUsername("banker");
    user.setEmail("banker@bankflow.local");
    user.setPassword("$2a$10$storedHash");
    user.setRoles(Set.of(role));
    user.setActive(active);
    user.setLockedUntil(lockedUntil);
    return CustomUserDetails.fromUser(user);
  }
}
