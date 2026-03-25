package com.bankflow.auth.service;

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
import com.bankflow.auth.security.CustomUserDetails;
import com.bankflow.common.exception.AccountLockedException;
import com.bankflow.common.exception.DuplicateResourceException;
import com.bankflow.common.exception.ResourceNotFoundException;
import com.bankflow.common.exception.UnauthorizedException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Core authentication business service.
 *
 * <p>Plain English: this class handles registration, login, refresh-token rotation, logout, and
 * account lockout logic for the Auth Service.
 *
 * <p>Design decision: refresh-token rotation and Redis-backed access-token blacklisting are kept in
 * the same service so session lifecycle rules stay consistent across every auth endpoint.
 *
 * <p>Security issue prevented: without centralizing these flows, token revocation and lockout rules
 * drift easily and create exploitable gaps.
 *
 * <p>Interview question answered: "How do you implement secure login, token rotation, and logout
 * in a Spring Boot microservice?"
 */
@Service
@Slf4j
public class AuthService {

  private static final int MAX_FAILED_ATTEMPTS = 5;
  private static final int LOCKOUT_MINUTES = 30;

  private final UserRepository userRepository;
  private final RoleRepository roleRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;
  private final RefreshTokenRepository refreshTokenRepository;
  private final StringRedisTemplate redisTemplate;
  private final Counter loginSuccesses;
  private final Counter loginFailures;
  private final Counter tokensBlacklisted;

  @Value("${jwt.refresh-expiration}")
  private long refreshTokenExpirationSeconds;

  public AuthService(
      UserRepository userRepository,
      RoleRepository roleRepository,
      PasswordEncoder passwordEncoder,
      JwtService jwtService,
      RefreshTokenRepository refreshTokenRepository,
      StringRedisTemplate redisTemplate,
      MeterRegistry meterRegistry) {
    MeterRegistry effectiveRegistry = meterRegistry != null ? meterRegistry : new SimpleMeterRegistry();
    this.userRepository = userRepository;
    this.roleRepository = roleRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtService = jwtService;
    this.refreshTokenRepository = refreshTokenRepository;
    this.redisTemplate = redisTemplate;
    this.loginSuccesses = Counter.builder("bankflow.auth.login.successes")
        .description("Successful login attempts")
        .register(effectiveRegistry);
    this.loginFailures = Counter.builder("bankflow.auth.login.failures")
        .description("Failed login attempts")
        .register(effectiveRegistry);
    this.tokensBlacklisted = Counter.builder("bankflow.auth.tokens.blacklisted")
        .description("Access tokens added to the Redis blacklist")
        .register(effectiveRegistry);
  }

  @Transactional
  public RegisterResponse register(RegisterRequest request) {
    if (userRepository.existsByEmailIgnoreCase(request.email())) {
      throw new DuplicateResourceException("Email already registered");
    }
    if (userRepository.existsByUsernameIgnoreCase(request.username())) {
      throw new DuplicateResourceException("Username already registered");
    }

    Role defaultRole = roleRepository.findByName(RoleName.ROLE_USER)
        .orElseThrow(() -> new IllegalStateException("Seeded role ROLE_USER is missing"));

    User user = new User();
    user.setUsername(request.username().trim());
    user.setEmail(request.email().trim().toLowerCase());
    user.setPassword(passwordEncoder.encode(request.password()));
    user.setRoles(Set.of(defaultRole));

    User savedUser = userRepository.save(user);
    return new RegisterResponse(savedUser.getId(), savedUser.getUsername(), savedUser.getEmail());
  }

  @Transactional
  public LoginResponse login(LoginRequest request) {
    User user = userRepository.findByUsernameOrEmail(request.usernameOrEmail())
        .orElseThrow(() -> {
          loginFailures.increment();
          return new ResourceNotFoundException("User", "usernameOrEmail", request.usernameOrEmail());
        });

    if (!user.isActive()) {
      loginFailures.increment();
      throw new UnauthorizedException("User account is inactive");
    }

    unlockIfLockWindowExpired(user);

    if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now())) {
      loginFailures.increment();
      throw new AccountLockedException(user.getUsername(), user.getLockedUntil());
    }

    if (!passwordEncoder.matches(request.password(), user.getPassword())) {
      loginFailures.increment();
      boolean locked = handleFailedLogin(user);
      if (locked) {
        throw new AccountLockedException(user.getUsername(), user.getLockedUntil());
      }
      throw new UnauthorizedException("Invalid credentials");
    }

    user.setFailedLoginAttempts(0);
    user.setLockedUntil(null);
    user.setLastLoginAt(LocalDateTime.now());
    User savedUser = userRepository.saveAndFlush(user);

    CustomUserDetails principal = CustomUserDetails.fromUser(savedUser);
    String accessToken = jwtService.generateAccessToken(principal);
    RefreshToken refreshToken = createAndSaveRefreshToken(savedUser);
    loginSuccesses.increment();
    return buildLoginResponse(savedUser, accessToken, refreshToken.getToken());
  }

  @Transactional
  public LoginResponse refreshToken(RefreshTokenRequest request) {
    RefreshToken existingToken = refreshTokenRepository.findByToken(request.refreshToken())
        .orElseThrow(() -> new UnauthorizedException("Refresh token not found"));

    if (existingToken.isRevoked()) {
      throw new UnauthorizedException("Token revoked");
    }
    if (existingToken.getExpiresAt().isBefore(LocalDateTime.now())) {
      throw new UnauthorizedException("Token expired");
    }

    revokeRefreshToken(existingToken);
    User user = existingToken.getUser();
    String newRefreshToken = createAndSaveRefreshToken(user).getToken();
    String newAccessToken = jwtService.generateAccessToken(CustomUserDetails.fromUser(user));
    return buildLoginResponse(user, newAccessToken, newRefreshToken);
  }

  @Transactional
  public void logout(String accessToken, UUID userId) {
    ensureUserExists(userId);
    jwtService.blacklistToken(accessToken);
    tokensBlacklisted.increment();
  }

  @Transactional
  public void logoutAllDevices(String accessToken, UUID userId) {
    ensureUserExists(userId);
    int revokedCount = refreshTokenRepository.revokeAllByUserId(userId);
    log.info("Revoked {} refresh tokens for user {}", revokedCount, userId);
    jwtService.blacklistToken(accessToken);
    tokensBlacklisted.increment();
  }

  @Transactional
  public UserProfileResponse getCurrentUserProfile(UUID userId) {
    User user = userRepository.findByIdWithRoles(userId)
        .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

    return new UserProfileResponse(
        user.getId(),
        user.getUsername(),
        user.getEmail(),
        mapRoles(user),
        user.getCreatedAt(),
        user.getLastLoginAt());
  }

  private void unlockIfLockWindowExpired(User user) {
    if (user.getLockedUntil() != null && user.getLockedUntil().isBefore(LocalDateTime.now())) {
      user.setFailedLoginAttempts(0);
      user.setLockedUntil(null);
    }
  }

  private boolean handleFailedLogin(User user) {
    user.setFailedLoginAttempts(user.getFailedLoginAttempts() + 1);
    boolean locked = false;
    if (user.getFailedLoginAttempts() >= MAX_FAILED_ATTEMPTS) {
      user.setLockedUntil(LocalDateTime.now().plusMinutes(LOCKOUT_MINUTES));
      locked = true;
    }
    userRepository.saveAndFlush(user);
    return locked;
  }

  private RefreshToken createAndSaveRefreshToken(User user) {
    RefreshToken refreshToken = new RefreshToken();
    refreshToken.setId(UUID.randomUUID());
    refreshToken.setToken(UUID.randomUUID().toString());
    refreshToken.setUser(user);
    refreshToken.setExpiresAt(LocalDateTime.now().plusSeconds(refreshTokenExpirationSeconds));
    refreshToken.setRevoked(false);
    return refreshTokenRepository.save(refreshToken);
  }

  private void revokeRefreshToken(RefreshToken refreshToken) {
    refreshToken.setRevoked(true);
    refreshTokenRepository.save(refreshToken);
  }

  private LoginResponse buildLoginResponse(User user, String accessToken, String refreshToken) {
    return new LoginResponse(
        accessToken,
        refreshToken,
        "Bearer",
        jwtService.getAccessTokenExpirationSeconds(),
        user.getId(),
        user.getUsername(),
        mapRoles(user));
  }

  private List<String> mapRoles(User user) {
    return user.getRoles().stream().map(role -> role.getName().name()).toList();
  }

  private void ensureUserExists(UUID userId) {
    userRepository.findById(userId)
        .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
  }
}
