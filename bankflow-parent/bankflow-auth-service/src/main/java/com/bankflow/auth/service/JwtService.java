package com.bankflow.auth.service;

import com.bankflow.auth.security.CustomUserDetails;
import com.bankflow.common.cache.CacheKeys;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import javax.crypto.SecretKey;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

/**
 * JWT creation, parsing, validation, and blacklist support.
 *
 * <p>Plain English: this service issues signed access tokens, verifies them on incoming requests,
 * and uses Redis to revoke tokens before they naturally expire.
 *
 * <p>Design decision: access tokens stay stateless for speed, but the {@code jti} claim is stored
 * in Redis on logout so a single token can be revoked without invalidating every session.
 *
 * <p>Security issue prevented: without a token identifier and blacklist lookup, a stolen access
 * token stays usable until expiry even after logout.
 *
 * <p>Interview question answered: "How do you revoke a stateless JWT before expiration?"
 */
@Service
@RequiredArgsConstructor
public class JwtService {

  private final StringRedisTemplate redisTemplate;

  @Value("${jwt.secret}")
  private String jwtSecret;

  @Value("${jwt.expiration}")
  private long jwtExpirationSeconds;

  /** Generates a signed access token with user, role, and JTI claims. */
  public String generateAccessToken(UserDetails userDetails) {
    if (!(userDetails instanceof CustomUserDetails principal)) {
      throw new IllegalArgumentException("UserDetails must be CustomUserDetails");
    }

    Instant now = Instant.now();
    Instant expiry = now.plusSeconds(jwtExpirationSeconds);
    String jti = UUID.randomUUID().toString();

    Map<String, Object> claims = new HashMap<>();
    claims.put("userId", principal.getId().toString());
    claims.put("email", principal.getEmail());
    claims.put("roles", principal.getRoleNames());
    claims.put("jti", jti);

    return Jwts.builder()
        .claims(claims)
        .subject(principal.getUsername())
        .issuedAt(Date.from(now))
        .expiration(Date.from(expiry))
        .signWith(getSigningKey(), Jwts.SIG.HS256)
        .compact();
  }

  /** Validates subject match, expiry, and Redis blacklist state for a JWT. */
  public boolean validateToken(String token, UserDetails userDetails) {
    try {
      String username = extractUsername(token);
      return username.equals(userDetails.getUsername())
          && !isTokenExpired(token)
          && !isTokenBlacklisted(token);
    } catch (JwtException | IllegalArgumentException ex) {
      return false;
    }
  }

  /** Reads the username stored in the JWT subject claim. */
  public String extractUsername(String token) {
    return extractClaim(token, Claims::getSubject);
  }

  /** Reads the unique JWT id used for blacklisting. */
  public String extractJti(String token) {
    return extractClaim(token, claims -> claims.get("jti", String.class));
  }

  /** Reads the user id custom claim from the token. */
  public UUID extractUserId(String token) {
    return UUID.fromString(extractClaim(token, claims -> claims.get("userId", String.class)));
  }

  /** Checks Redis for a blacklist marker matching this token's JTI. */
  public boolean isTokenBlacklisted(String token) {
    String jti = extractJti(token);
    return Boolean.TRUE.equals(redisTemplate.hasKey(CacheKeys.BLACKLIST_PREFIX + jti));
  }

  /** Blacklists the token for exactly its remaining lifetime. */
  public void blacklistToken(String token) {
    String jti = extractJti(token);
    long ttlSeconds = extractExpiration(token).getTime() / 1000 - Instant.now().getEpochSecond();
    if (ttlSeconds <= 0) {
      return;
    }

    redisTemplate.opsForValue().set(
        CacheKeys.BLACKLIST_PREFIX + jti,
        "revoked",
        ttlSeconds,
        TimeUnit.SECONDS);
  }

  /** Returns the configured access-token lifetime in seconds. */
  public long getAccessTokenExpirationSeconds() {
    return jwtExpirationSeconds;
  }

  /** Extracts a single claim from a validated JWT. */
  private <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
    return claimsResolver.apply(extractAllClaims(token));
  }

  /** Parses and verifies the JWT signature before returning claims. */
  private Claims extractAllClaims(String token) {
    return Jwts.parser()
        .verifyWith(getSigningKey())
        .build()
        .parseSignedClaims(token)
        .getPayload();
  }

  /** Checks whether the JWT expiration timestamp has passed. */
  private boolean isTokenExpired(String token) {
    return extractExpiration(token).before(new Date());
  }

  /** Reads the expiration timestamp from the token. */
  private Date extractExpiration(String token) {
    return extractClaim(token, Claims::getExpiration);
  }

  /** Builds the HMAC signing key from the configured shared secret. */
  private SecretKey getSigningKey() {
    byte[] keyBytes;
    try {
      keyBytes = Decoders.BASE64.decode(jwtSecret);
    } catch (IllegalArgumentException ex) {
      keyBytes = jwtSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }
    return Keys.hmacShaKeyFor(keyBytes);
  }
}
