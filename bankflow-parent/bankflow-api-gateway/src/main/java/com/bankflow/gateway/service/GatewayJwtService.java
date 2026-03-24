package com.bankflow.gateway.service;

import com.bankflow.common.cache.CacheKeys;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.util.Date;
import java.util.List;
import java.util.function.Function;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Reactive JWT parsing and blacklist support for the gateway.
 *
 * <p>Plain English: the gateway validates the same JWT structure issued by auth-service and checks
 * Redis for token revocation before routing requests to internal services.
 */
@Service
public class GatewayJwtService {

  private final ReactiveStringRedisTemplate redisTemplate;

  @Value("${jwt.secret}")
  private String jwtSecret;

  public GatewayJwtService(ReactiveStringRedisTemplate redisTemplate) {
    this.redisTemplate = redisTemplate;
  }

  public boolean isTokenValid(String token) {
    try {
      return !extractExpiration(token).before(new Date());
    } catch (JwtException | IllegalArgumentException ex) {
      return false;
    }
  }

  public Mono<Boolean> isTokenBlacklisted(String token) {
    String jti = extractClaim(token, claims -> claims.get("jti", String.class));
    return redisTemplate.hasKey(CacheKeys.BLACKLIST_PREFIX + jti);
  }

  public String extractUserId(String token) {
    return extractClaim(token, claims -> claims.get("userId", String.class));
  }

  @SuppressWarnings("unchecked")
  public List<String> extractRoles(String token) {
    Object roles = extractClaim(token, claims -> claims.get("roles"));
    if (roles instanceof List<?> roleList) {
      return roleList.stream().map(String::valueOf).toList();
    }
    return List.of();
  }

  private Date extractExpiration(String token) {
    return extractClaim(token, Claims::getExpiration);
  }

  private <T> T extractClaim(String token, Function<Claims, T> claimResolver) {
    return claimResolver.apply(Jwts.parser().verifyWith(getSigningKey()).build().parseSignedClaims(token).getPayload());
  }

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
