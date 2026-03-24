package com.bankflow.gateway.filter;

import com.bankflow.common.api.ApiResponse;
import com.bankflow.common.error.ErrorCode;
import com.bankflow.gateway.service.GatewayJwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.JwtException;
import java.util.List;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Validates JWTs at the gateway and forwards trusted identity headers downstream.
 *
 * <p>Plain English: this is the single authentication checkpoint for BankFlow's internal services.
 * Downstream services trust the `X-User-Id` and `X-User-Roles` headers only because the gateway
 * stamped them after signature and blacklist validation.
 */
@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

  private static final List<String> PUBLIC_PATHS = List.of(
      "/api/v1/auth/register",
      "/api/v1/auth/login",
      "/api/v1/auth/refresh-token",
      "/actuator/**",
      "/fallback/**");

  private final GatewayJwtService gatewayJwtService;
  private final ObjectMapper objectMapper;
  private final AntPathMatcher antPathMatcher = new AntPathMatcher();

  public JwtAuthenticationFilter(GatewayJwtService gatewayJwtService, ObjectMapper objectMapper) {
    this.gatewayJwtService = gatewayJwtService;
    this.objectMapper = objectMapper;
  }

  @Override
  public int getOrder() {
    return -100;
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    String path = exchange.getRequest().getPath().value();
    if (isPublicPath(path)) {
      return chain.filter(exchange);
    }

    String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
      return unauthorizedResponse(exchange, "Missing or invalid token");
    }

    String token = authHeader.substring(7);
    if (!gatewayJwtService.isTokenValid(token)) {
      return unauthorizedResponse(exchange, "Invalid token");
    }

    try {
      return gatewayJwtService.isTokenBlacklisted(token)
          .flatMap(blacklisted -> {
            if (Boolean.TRUE.equals(blacklisted)) {
              return unauthorizedResponse(exchange, "Token revoked");
            }

            String userId = gatewayJwtService.extractUserId(token);
            String roles = String.join(",", gatewayJwtService.extractRoles(token));

            ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .header("X-User-Id", userId)
                .header("X-User-Roles", roles)
                .build();

            return chain.filter(exchange.mutate().request(mutatedRequest).build());
          })
          .onErrorResume(ex -> unauthorizedResponse(exchange, "Token validation failed"));
    } catch (JwtException | IllegalArgumentException ex) {
      return unauthorizedResponse(exchange, "Token validation failed");
    }
  }

  private boolean isPublicPath(String path) {
    return PUBLIC_PATHS.stream().anyMatch(pattern -> antPathMatcher.match(pattern, path));
  }

  private Mono<Void> unauthorizedResponse(ServerWebExchange exchange, String message) {
    try {
      exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
      exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
      byte[] responseBody = objectMapper.writeValueAsBytes(ApiResponse.error(message, ErrorCode.UNAUTHORIZED.name()));
      DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(responseBody);
      return exchange.getResponse().writeWith(Mono.just(buffer));
    } catch (Exception ex) {
      return Mono.error(ex);
    }
  }
}
