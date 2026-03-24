package com.bankflow.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bankflow.auth.entity.Role;
import com.bankflow.auth.entity.RoleName;
import com.bankflow.auth.entity.User;
import com.bankflow.auth.security.CustomUserDetails;
import com.bankflow.common.cache.CacheKeys;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Unit tests for {@link JwtService}.
 *
 * <p>Plain English: these tests verify that token generation, validation, expiry handling, and
 * Redis-backed blacklisting behave exactly as the auth filter expects in production.
 *
 * <p>Design decision: this suite avoids a Spring context so failures isolate pure JWT logic rather
 * than container wiring or infrastructure startup noise.
 *
 * <p>Bug prevented: if claim names, expiry math, or blacklist TTL handling drift, tokens can remain
 * valid after logout or fail unexpectedly on live requests.
 *
 * <p>Interview question answered: "How do you unit test JWT issuance and revocation logic without
 * bringing up the whole application?"
 */
@ExtendWith(MockitoExtension.class)
class JwtServiceTest {

  private static final String TEST_SECRET = Base64.getEncoder().encodeToString(
      "bankflow-test-jwt-secret-key-should-be-long-enough-for-unit-tests-123456789"
          .getBytes(StandardCharsets.UTF_8));

  @Mock
  private StringRedisTemplate redisTemplate;

  @Mock
  private ValueOperations<String, String> valueOperations;

  @InjectMocks
  private JwtService jwtService;

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(jwtService, "jwtSecret", TEST_SECRET);
    ReflectionTestUtils.setField(jwtService, "jwtExpirationSeconds", 900L);
    lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
  }

  @Test
  @DisplayName("JWT generation should embed username, user id, roles, jti, and a 15 minute expiry")
  void generateAccessToken_shouldContainCorrectClaims() {
    // Arrange
    CustomUserDetails userDetails = CustomUserDetails.fromUser(buildUser("banker"));
    Instant before = Instant.now();

    // Act
    String token = jwtService.generateAccessToken(userDetails);
    Claims claims = parseClaims(token);
    Instant after = Instant.now();

    // Assert
    assertThat(claims.getSubject()).isEqualTo("banker");
    assertThat(claims.get("userId", String.class)).isEqualTo(userDetails.getId().toString());
    assertThat(claims.get("email", String.class)).isEqualTo("banker@bankflow.local");
    assertThat(claims.get("roles", java.util.List.class)).containsExactly("ROLE_USER");
    assertThat(claims.get("jti", String.class)).isNotBlank();
    assertThat(claims.getExpiration().toInstant())
        .isBetween(before.plusSeconds(895), after.plusSeconds(905));
  }

  @Test
  @DisplayName("JWT generation should expire the access token in roughly 15 minutes")
  void generateAccessToken_shouldExpireIn15Minutes() {
    // Arrange
    CustomUserDetails userDetails = CustomUserDetails.fromUser(buildUser("auditor"));
    Instant before = Instant.now();

    // Act
    String token = jwtService.generateAccessToken(userDetails);
    Claims claims = parseClaims(token);
    Instant after = Instant.now();

    // Assert
    long secondsFromBefore = claims.getExpiration().toInstant().getEpochSecond() - before.getEpochSecond();
    long secondsFromAfter = claims.getExpiration().toInstant().getEpochSecond() - after.getEpochSecond();
    assertThat(secondsFromBefore).isBetween(895L, 905L);
    assertThat(secondsFromAfter).isBetween(895L, 905L);
  }

  @Test
  @DisplayName("Token validation should succeed when subject, signature, expiry, and blacklist checks all pass")
  void validateToken_withValidToken_shouldReturnTrue() {
    // Arrange
    CustomUserDetails userDetails = CustomUserDetails.fromUser(buildUser("operator"));
    String token = jwtService.generateAccessToken(userDetails);
    String jti = jwtService.extractJti(token);
    when(redisTemplate.hasKey(CacheKeys.BLACKLIST_PREFIX + jti)).thenReturn(false);

    // Act
    boolean valid = jwtService.validateToken(token, userDetails);

    // Assert
    assertThat(valid).isTrue();
  }

  @Test
  @DisplayName("Token validation should fail when the token is already expired")
  void validateToken_withExpiredToken_shouldReturnFalse() {
    // Arrange
    CustomUserDetails userDetails = CustomUserDetails.fromUser(buildUser("expired-user"));
    String expiredToken = createToken(userDetails, -1);

    // Act
    boolean valid = jwtService.validateToken(expiredToken, userDetails);

    // Assert
    assertThat(valid).isFalse();
  }

  @Test
  @DisplayName("Token validation should fail when the signature is tampered with")
  void validateToken_withTamperedSignature_shouldReturnFalse() {
    // Arrange
    CustomUserDetails userDetails = CustomUserDetails.fromUser(buildUser("tampered-user"));
    String validToken = jwtService.generateAccessToken(userDetails);
    String tamperedToken = validToken.substring(0, validToken.length() - 3) + "abc";

    // Act
    boolean valid = jwtService.validateToken(tamperedToken, userDetails);

    // Assert
    assertThat(valid).isFalse();
  }

  @Test
  @DisplayName("Token validation should fail when Redis says the JWT id is blacklisted")
  void validateToken_withBlacklistedToken_shouldReturnFalse() {
    // Arrange
    CustomUserDetails userDetails = CustomUserDetails.fromUser(buildUser("revoked-user"));
    String token = jwtService.generateAccessToken(userDetails);
    String jti = jwtService.extractJti(token);
    when(redisTemplate.hasKey(CacheKeys.BLACKLIST_PREFIX + jti)).thenReturn(true);

    // Act
    boolean valid = jwtService.validateToken(token, userDetails);

    // Assert
    assertThat(valid).isFalse();
  }

  @Test
  @DisplayName("Blacklisting should store the JWT id in Redis with the token's remaining lifetime")
  void blacklistToken_shouldStoreInRedisWithRemainingTtl() {
    // Arrange
    CustomUserDetails userDetails = CustomUserDetails.fromUser(buildUser("logout-user"));
    String token = jwtService.generateAccessToken(userDetails);
    String jti = jwtService.extractJti(token);
    ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<Long> ttlCaptor = ArgumentCaptor.forClass(Long.class);
    ArgumentCaptor<TimeUnit> timeUnitCaptor = ArgumentCaptor.forClass(TimeUnit.class);

    // Act
    jwtService.blacklistToken(token);

    // Assert
    verify(valueOperations).set(
        keyCaptor.capture(),
        valueCaptor.capture(),
        ttlCaptor.capture(),
        timeUnitCaptor.capture());
    assertThat(keyCaptor.getValue()).isEqualTo(CacheKeys.BLACKLIST_PREFIX + jti);
    assertThat(valueCaptor.getValue()).isEqualTo("revoked");
    assertThat(ttlCaptor.getValue()).isGreaterThan(0L).isLessThanOrEqualTo(900L);
    assertThat(timeUnitCaptor.getValue()).isEqualTo(TimeUnit.SECONDS);
  }

  @Test
  @DisplayName("User id extraction should return the UUID stored inside the custom JWT claim")
  void extractUserId_shouldReturnStoredClaim() {
    // Arrange
    CustomUserDetails userDetails = CustomUserDetails.fromUser(buildUser("claims-user"));
    String token = jwtService.generateAccessToken(userDetails);

    // Act
    UUID extractedUserId = jwtService.extractUserId(token);

    // Assert
    assertThat(extractedUserId).isEqualTo(userDetails.getId());
  }

  private Claims parseClaims(String token) {
    return Jwts.parser()
        .verifyWith(getSigningKey())
        .build()
        .parseSignedClaims(token)
        .getPayload();
  }

  private String createToken(CustomUserDetails userDetails, long expirationOffsetSeconds) {
    Instant now = Instant.now();
    Instant expiry = now.plusSeconds(expirationOffsetSeconds);
    return Jwts.builder()
        .subject(userDetails.getUsername())
        .claim("userId", userDetails.getId().toString())
        .claim("email", userDetails.getEmail())
        .claim("roles", userDetails.getRoleNames())
        .claim("jti", UUID.randomUUID().toString())
        .issuedAt(Date.from(now))
        .expiration(Date.from(expiry))
        .signWith(getSigningKey(), Jwts.SIG.HS256)
        .compact();
  }

  private SecretKey getSigningKey() {
    byte[] keyBytes;
    try {
      keyBytes = Decoders.BASE64.decode(TEST_SECRET);
    } catch (IllegalArgumentException ex) {
      keyBytes = TEST_SECRET.getBytes(StandardCharsets.UTF_8);
    }
    return Keys.hmacShaKeyFor(keyBytes);
  }

  private User buildUser(String username) {
    Role role = new Role();
    role.setId(1L);
    role.setName(RoleName.ROLE_USER);

    User user = new User();
    user.setId(UUID.randomUUID());
    user.setUsername(username);
    user.setEmail(username + "@bankflow.local");
    user.setPassword("$2a$10$hashedPasswordValue");
    user.setRoles(Set.of(role));
    user.setActive(true);
    return user;
  }
}
