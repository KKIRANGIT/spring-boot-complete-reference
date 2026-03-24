package com.bankflow.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bankflow.auth.dto.LoginRequest;
import com.bankflow.auth.dto.LoginResponse;
import com.bankflow.auth.dto.RefreshTokenRequest;
import com.bankflow.auth.dto.RegisterRequest;
import com.bankflow.auth.dto.RegisterResponse;
import com.bankflow.auth.dto.UserProfileResponse;
import com.bankflow.auth.entity.RefreshToken;
import com.bankflow.auth.entity.Role;
import com.bankflow.auth.entity.RoleName;
import com.bankflow.auth.entity.User;
import com.bankflow.auth.repository.RefreshTokenRepository;
import com.bankflow.auth.repository.RoleRepository;
import com.bankflow.auth.repository.UserRepository;
import com.bankflow.common.exception.AccountLockedException;
import com.bankflow.common.exception.DuplicateResourceException;
import com.bankflow.common.exception.UnauthorizedException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Unit tests for {@link AuthService}.
 *
 * <p>Plain English: these tests exercise registration, credential validation, lockout handling,
 * refresh-token rotation, logout, and profile lookup with all collaborators mocked.
 *
 * <p>Design decision: pure Mockito tests keep the business rules under a microscope so failures
 * point to auth logic rather than HTTP serialization or Spring wiring.
 *
 * <p>Bug prevented: lockout counters, token rotation, and password hashing are easy to regress, and
 * those regressions become security incidents instead of cosmetic defects.
 *
 * <p>Interview question answered: "How do you test banking auth rules like lockout and refresh
 * token rotation without starting the full application?"
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

  @Mock
  private UserRepository userRepository;

  @Mock
  private RoleRepository roleRepository;

  @Mock
  private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

  @Mock
  private JwtService jwtService;

  @Mock
  private RefreshTokenRepository refreshTokenRepository;

  @Mock
  private StringRedisTemplate redisTemplate;

  @InjectMocks
  private AuthService authService;

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(authService, "refreshTokenExpirationSeconds", 604800L);

    lenient().when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
      User user = invocation.getArgument(0);
      if (user.getId() == null) {
        user.setId(UUID.randomUUID());
      }
      return user;
    });
    lenient().when(userRepository.saveAndFlush(any(User.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    lenient().when(refreshTokenRepository.save(any(RefreshToken.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
  }

  @Test
  @DisplayName("Registration should persist a new user with a BCrypt hash instead of the plain password")
  void register_withValidData_shouldCreateUser() {
    // Arrange
    RegisterRequest request = new RegisterRequest("banker", "banker@bankflow.local", "Password1!");
    Role role = buildRole(RoleName.ROLE_USER);
    when(userRepository.existsByEmailIgnoreCase(request.email())).thenReturn(false);
    when(userRepository.existsByUsernameIgnoreCase(request.username())).thenReturn(false);
    when(roleRepository.findByName(RoleName.ROLE_USER)).thenReturn(Optional.of(role));
    when(passwordEncoder.encode(request.password())).thenReturn("$2a$10$encodedPassword");
    ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);

    // Act
    RegisterResponse response = authService.register(request);

    // Assert
    verify(userRepository).save(userCaptor.capture());
    User savedUser = userCaptor.getValue();
    assertThat(savedUser.getPassword()).isEqualTo("$2a$10$encodedPassword").isNotEqualTo(request.password());
    assertThat(savedUser.getRoles()).extracting(userRole -> userRole.getName().name()).containsExactly("ROLE_USER");
    assertThat(response.userId()).isNotNull();
    assertThat(response.username()).isEqualTo("banker");
    assertThat(response.email()).isEqualTo("banker@bankflow.local");
  }

  @Test
  @DisplayName("Registration should reject a duplicate email before any hashing or database save happens")
  void register_withDuplicateEmail_shouldThrowException() {
    // Arrange
    RegisterRequest request = new RegisterRequest("banker", "banker@bankflow.local", "Password1!");
    when(userRepository.existsByEmailIgnoreCase(request.email())).thenReturn(true);

    // Act + Assert
    assertThatThrownBy(() -> authService.register(request))
        .isInstanceOf(DuplicateResourceException.class)
        .hasMessageContaining("Email already registered");
    verify(userRepository, never()).save(any(User.class));
    verify(passwordEncoder, never()).encode(any());
  }

  @Test
  @DisplayName("Registration should reject a duplicate username before user creation starts")
  void register_withDuplicateUsername_shouldThrowException() {
    // Arrange
    RegisterRequest request = new RegisterRequest("banker", "banker@bankflow.local", "Password1!");
    when(userRepository.existsByEmailIgnoreCase(request.email())).thenReturn(false);
    when(userRepository.existsByUsernameIgnoreCase(request.username())).thenReturn(true);

    // Act + Assert
    assertThatThrownBy(() -> authService.register(request))
        .isInstanceOf(DuplicateResourceException.class)
        .hasMessageContaining("Username already registered");
    verify(userRepository, never()).save(any(User.class));
  }

  @Test
  @DisplayName("Login should issue tokens, clear failure counters, and update the last login timestamp")
  void login_withValidCredentials_shouldReturnTokensAndUpdateLastLogin() {
    // Arrange
    LoginRequest request = new LoginRequest("banker", "Password1!");
    User user = buildUser("banker", "banker@bankflow.local");
    when(userRepository.findByUsernameOrEmail(request.usernameOrEmail())).thenReturn(Optional.of(user));
    when(passwordEncoder.matches(request.password(), user.getPassword())).thenReturn(true);
    when(jwtService.generateAccessToken(any())).thenReturn("access-token");
    when(jwtService.getAccessTokenExpirationSeconds()).thenReturn(900L);
    ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);

    // Act
    LoginResponse response = authService.login(request);

    // Assert
    verify(userRepository).saveAndFlush(userCaptor.capture());
    User persistedUser = userCaptor.getValue();
    assertThat(persistedUser.getFailedLoginAttempts()).isZero();
    assertThat(persistedUser.getLockedUntil()).isNull();
    assertThat(persistedUser.getLastLoginAt()).isNotNull();
    assertThat(response.accessToken()).isEqualTo("access-token");
    assertThat(response.refreshToken()).isNotBlank();
    assertThat(response.username()).isEqualTo("banker");
    assertThat(response.roles()).containsExactly("ROLE_USER");
  }

  @Test
  @DisplayName("Wrong password should increment failed attempts so brute-force attempts are tracked")
  void login_withWrongPassword_shouldIncrementFailedAttempts() {
    // Arrange
    LoginRequest request = new LoginRequest("banker", "WrongPassword1!");
    User user = buildUser("banker", "banker@bankflow.local");
    user.setFailedLoginAttempts(2);
    when(userRepository.findByUsernameOrEmail(request.usernameOrEmail())).thenReturn(Optional.of(user));
    when(passwordEncoder.matches(request.password(), user.getPassword())).thenReturn(false);
    ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);

    // Act + Assert
    assertThatThrownBy(() -> authService.login(request))
        .isInstanceOf(UnauthorizedException.class)
        .hasMessageContaining("Invalid credentials");
    verify(userRepository).saveAndFlush(userCaptor.capture());
    assertThat(userCaptor.getValue().getFailedLoginAttempts()).isEqualTo(3);
    assertThat(userCaptor.getValue().getLockedUntil()).isNull();
  }

  @Test
  @DisplayName("The fifth failed login should lock the account for roughly thirty minutes")
  void login_withFiveFailures_shouldLockAccount() {
    // Arrange
    LoginRequest request = new LoginRequest("banker", "WrongPassword1!");
    User user = buildUser("banker", "banker@bankflow.local");
    user.setFailedLoginAttempts(4);
    when(userRepository.findByUsernameOrEmail(request.usernameOrEmail())).thenReturn(Optional.of(user));
    when(passwordEncoder.matches(request.password(), user.getPassword())).thenReturn(false);
    ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
    LocalDateTime before = LocalDateTime.now();

    // Act + Assert
    assertThatThrownBy(() -> authService.login(request)).isInstanceOf(AccountLockedException.class);
    LocalDateTime after = LocalDateTime.now();
    verify(userRepository).saveAndFlush(userCaptor.capture());
    User persistedUser = userCaptor.getValue();
    assertThat(persistedUser.getFailedLoginAttempts()).isEqualTo(5);
    assertThat(persistedUser.getLockedUntil()).isNotNull();
    assertThat(persistedUser.getLockedUntil())
        .isAfterOrEqualTo(before.plusMinutes(29))
        .isBeforeOrEqualTo(after.plusMinutes(31));
  }

  @Test
  @DisplayName("A user still inside the lock window should be blocked before password verification runs")
  void login_withLockedAccountWithinWindow_shouldThrowAccountLockedException() {
    // Arrange
    LoginRequest request = new LoginRequest("banker", "Password1!");
    User user = buildUser("banker", "banker@bankflow.local");
    user.setLockedUntil(LocalDateTime.now().plusMinutes(20));
    when(userRepository.findByUsernameOrEmail(request.usernameOrEmail())).thenReturn(Optional.of(user));

    // Act + Assert
    assertThatThrownBy(() -> authService.login(request)).isInstanceOf(AccountLockedException.class);
    verify(passwordEncoder, never()).matches(any(), any());
  }

  @Test
  @DisplayName("An expired lock should clear automatically so the next correct login succeeds")
  void login_withExpiredLock_shouldAutoUnlockAndLogin() {
    // Arrange
    LoginRequest request = new LoginRequest("banker", "Password1!");
    User user = buildUser("banker", "banker@bankflow.local");
    user.setFailedLoginAttempts(4);
    user.setLockedUntil(LocalDateTime.now().minusMinutes(5));
    when(userRepository.findByUsernameOrEmail(request.usernameOrEmail())).thenReturn(Optional.of(user));
    when(passwordEncoder.matches(request.password(), user.getPassword())).thenReturn(true);
    when(jwtService.generateAccessToken(any())).thenReturn("fresh-access-token");
    when(jwtService.getAccessTokenExpirationSeconds()).thenReturn(900L);
    ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);

    // Act
    LoginResponse response = authService.login(request);

    // Assert
    verify(userRepository).saveAndFlush(userCaptor.capture());
    User persistedUser = userCaptor.getValue();
    assertThat(persistedUser.getFailedLoginAttempts()).isZero();
    assertThat(persistedUser.getLockedUntil()).isNull();
    assertThat(response.accessToken()).isEqualTo("fresh-access-token");
    assertThat(response.refreshToken()).isNotBlank();
  }

  @Test
  @DisplayName("Refresh token rotation should revoke the old token and save a brand new one")
  void refreshToken_withValidToken_shouldRotateTokens() {
    // Arrange
    User user = buildUser("banker", "banker@bankflow.local");
    RefreshToken existingToken = new RefreshToken();
    existingToken.setId(UUID.randomUUID());
    existingToken.setToken("existing-refresh-token");
    existingToken.setUser(user);
    existingToken.setExpiresAt(LocalDateTime.now().plusDays(1));
    existingToken.setRevoked(false);
    when(refreshTokenRepository.findByToken("existing-refresh-token")).thenReturn(Optional.of(existingToken));
    when(jwtService.generateAccessToken(any())).thenReturn("new-access-token");
    when(jwtService.getAccessTokenExpirationSeconds()).thenReturn(900L);
    ArgumentCaptor<RefreshToken> tokenCaptor = ArgumentCaptor.forClass(RefreshToken.class);

    // Act
    LoginResponse response = authService.refreshToken(new RefreshTokenRequest("existing-refresh-token"));

    // Assert
    verify(refreshTokenRepository, org.mockito.Mockito.times(2)).save(tokenCaptor.capture());
    List<RefreshToken> savedTokens = tokenCaptor.getAllValues();
    assertThat(savedTokens.get(0).isRevoked()).isTrue();
    assertThat(savedTokens.get(1).isRevoked()).isFalse();
    assertThat(savedTokens.get(1).getToken()).isNotEqualTo("existing-refresh-token");
    assertThat(response.accessToken()).isEqualTo("new-access-token");
    assertThat(response.refreshToken()).isEqualTo(savedTokens.get(1).getToken());
  }

  @Test
  @DisplayName("Refreshing with an already revoked token should fail immediately")
  void refreshToken_withRevokedToken_shouldThrowUnauthorizedException() {
    // Arrange
    RefreshToken revokedToken = new RefreshToken();
    revokedToken.setToken("revoked-refresh-token");
    revokedToken.setRevoked(true);
    revokedToken.setExpiresAt(LocalDateTime.now().plusDays(1));
    revokedToken.setUser(buildUser("banker", "banker@bankflow.local"));
    when(refreshTokenRepository.findByToken("revoked-refresh-token")).thenReturn(Optional.of(revokedToken));

    // Act + Assert
    assertThatThrownBy(() -> authService.refreshToken(new RefreshTokenRequest("revoked-refresh-token")))
        .isInstanceOf(UnauthorizedException.class)
        .hasMessageContaining("Token revoked");
  }

  @Test
  @DisplayName("Refreshing with an expired token should fail so stolen stale tokens cannot be reused")
  void refreshToken_withExpiredToken_shouldThrowUnauthorizedException() {
    // Arrange
    RefreshToken expiredToken = new RefreshToken();
    expiredToken.setToken("expired-refresh-token");
    expiredToken.setRevoked(false);
    expiredToken.setExpiresAt(LocalDateTime.now().minusSeconds(1));
    expiredToken.setUser(buildUser("banker", "banker@bankflow.local"));
    when(refreshTokenRepository.findByToken("expired-refresh-token")).thenReturn(Optional.of(expiredToken));

    // Act + Assert
    assertThatThrownBy(() -> authService.refreshToken(new RefreshTokenRequest("expired-refresh-token")))
        .isInstanceOf(UnauthorizedException.class)
        .hasMessageContaining("Token expired");
  }

  @Test
  @DisplayName("Logout should blacklist the presented access token so it stops working immediately")
  void logout_shouldBlacklistAccessToken() {
    // Arrange
    UUID userId = UUID.randomUUID();
    when(userRepository.findById(userId)).thenReturn(Optional.of(buildUser("banker", "banker@bankflow.local")));

    // Act
    authService.logout("current-access-token", userId);

    // Assert
    verify(jwtService).blacklistToken("current-access-token");
  }

  @Test
  @DisplayName("Logout all devices should revoke every refresh token and blacklist the current JWT")
  void logoutAllDevices_shouldRevokeAllTokensAndBlacklistAccessToken() {
    // Arrange
    UUID userId = UUID.randomUUID();
    when(userRepository.findById(userId)).thenReturn(Optional.of(buildUser("banker", "banker@bankflow.local")));
    when(refreshTokenRepository.revokeAllByUserId(userId)).thenReturn(3);

    // Act
    authService.logoutAllDevices("current-access-token", userId);

    // Assert
    verify(refreshTokenRepository).revokeAllByUserId(userId);
    verify(jwtService).blacklistToken("current-access-token");
  }

  @Test
  @DisplayName("Profile lookup should return the authenticated user's basic identity data")
  void getCurrentUserProfile_shouldReturnProfile() {
    // Arrange
    User user = buildUser("banker", "banker@bankflow.local");
    user.setCreatedAt(LocalDateTime.now().minusDays(7));
    user.setLastLoginAt(LocalDateTime.now().minusMinutes(5));
    when(userRepository.findByIdWithRoles(user.getId())).thenReturn(Optional.of(user));

    // Act
    UserProfileResponse response = authService.getCurrentUserProfile(user.getId());

    // Assert
    assertThat(response.id()).isEqualTo(user.getId());
    assertThat(response.username()).isEqualTo("banker");
    assertThat(response.email()).isEqualTo("banker@bankflow.local");
    assertThat(response.roles()).containsExactly("ROLE_USER");
    assertThat(response.createdAt()).isEqualTo(user.getCreatedAt());
    assertThat(response.lastLoginAt()).isEqualTo(user.getLastLoginAt());
  }

  private User buildUser(String username, String email) {
    User user = new User();
    user.setId(UUID.randomUUID());
    user.setUsername(username);
    user.setEmail(email);
    user.setPassword("$2a$10$storedHash");
    user.setRoles(Set.of(buildRole(RoleName.ROLE_USER)));
    user.setActive(true);
    user.setFailedLoginAttempts(0);
    return user;
  }

  private Role buildRole(RoleName roleName) {
    Role role = new Role();
    role.setId(1L);
    role.setName(roleName);
    return role;
  }
}
