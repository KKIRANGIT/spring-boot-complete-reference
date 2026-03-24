package com.bankflow.payment.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Trusts gateway-stamped identity headers for internal payment-service requests.
 *
 * <p>Plain English: payment-service does not validate JWTs itself; it consumes `X-User-Id` and
 * `X-User-Roles` after the API gateway has already authenticated the caller.
 */
@Component
public class GatewayAuthenticationFilter extends OncePerRequestFilter {

  private static final String USER_ID_HEADER = "X-User-Id";
  private static final String USER_ROLES_HEADER = "X-User-Roles";

  @Override
  protected void doFilterInternal(
      HttpServletRequest request,
      HttpServletResponse response,
      FilterChain filterChain) throws ServletException, IOException {
    String userIdHeader = request.getHeader(USER_ID_HEADER);
    if (userIdHeader == null || userIdHeader.isBlank()) {
      filterChain.doFilter(request, response);
      return;
    }

    if (SecurityContextHolder.getContext().getAuthentication() == null) {
      try {
        UUID userId = UUID.fromString(userIdHeader);
        UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(
                userId.toString(),
                null,
                parseAuthorities(request.getHeader(USER_ROLES_HEADER)));
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
      } catch (IllegalArgumentException ex) {
        SecurityContextHolder.clearContext();
      }
    }

    filterChain.doFilter(request, response);
  }

  private List<SimpleGrantedAuthority> parseAuthorities(String rawRolesHeader) {
    if (rawRolesHeader == null || rawRolesHeader.isBlank()) {
      return List.of();
    }

    return Arrays.stream(rawRolesHeader.split(","))
        .map(String::trim)
        .filter(role -> !role.isBlank())
        .map(role -> role.startsWith("ROLE_") ? role : "ROLE_" + role)
        .distinct()
        .map(SimpleGrantedAuthority::new)
        .toList();
  }
}
