package com.bankflow.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bankflow.auth.entity.Role;
import com.bankflow.auth.entity.RoleName;
import com.bankflow.auth.entity.User;
import com.bankflow.auth.repository.UserRepository;
import com.bankflow.auth.service.JwtService;
import com.bankflow.common.error.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.JwtException;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

/**
 * Unit tests for security support classes that are not exercised by the pure service tests.
 *
 * <p>Plain English: this suite covers principal mapping, user loading, JWT filter behavior, and
 * the JSON 401/403 handlers so the auth service's security plumbing is verified without Docker or
 * a Spring context.
 */
@ExtendWith(MockitoExtension.class)
class AuthSecuritySupportTest {

  @Mock
  private UserRepository userRepository;

  @Mock
  private JwtService jwtService;

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  @DisplayName("Custom user details should expose roles, credentials, and account flags correctly")
  void customUserDetails_shouldExposeSecurityFlags() {
    // Arrange
    User unlockedUser = buildUser(true, null);
    User lockedUser = buildUser(false, LocalDateTime.now().plusMinutes(10));

    // Act
    CustomUserDetails unlockedPrincipal = CustomUserDetails.fromUser(unlockedUser);
    CustomUserDetails lockedPrincipal = CustomUserDetails.fromUser(lockedUser);

    // Assert
    assertThat(unlockedPrincipal.getAuthorities()).extracting("authority").containsExactly("ROLE_USER");
    assertThat(unlockedPrincipal.getPassword()).isEqualTo("$2a$10$storedHash");
    assertThat(unlockedPrincipal.isAccountNonExpired()).isTrue();
    assertThat(unlockedPrincipal.isAccountNonLocked()).isTrue();
    assertThat(unlockedPrincipal.isCredentialsNonExpired()).isTrue();
    assertThat(unlockedPrincipal.isEnabled()).isTrue();
    assertThat(lockedPrincipal.isAccountNonLocked()).isFalse();
    assertThat(lockedPrincipal.isEnabled()).isFalse();
  }

  @Test
  @DisplayName("User details service should load a principal with roles when the user exists")
  void customUserDetailsService_shouldLoadUser() {
    // Arrange
    CustomUserDetailsService customUserDetailsService = new CustomUserDetailsService(userRepository);
    User user = buildUser(true, null);
    when(userRepository.findByUsernameWithRoles("banker")).thenReturn(java.util.Optional.of(user));

    // Act
    CustomUserDetails principal = (CustomUserDetails) customUserDetailsService.loadUserByUsername("banker");

    // Assert
    assertThat(principal.getUsername()).isEqualTo("banker");
    assertThat(principal.getEmail()).isEqualTo("banker@bankflow.local");
    assertThat(principal.getRoleNames()).containsExactly("ROLE_USER");
  }

  @Test
  @DisplayName("User details service should throw a Spring Security exception when the user is missing")
  void customUserDetailsService_shouldThrowWhenUserMissing() {
    // Arrange
    CustomUserDetailsService customUserDetailsService = new CustomUserDetailsService(userRepository);
    when(userRepository.findByUsernameWithRoles("missing")).thenReturn(java.util.Optional.empty());

    // Act + Assert
    assertThatThrownBy(() -> customUserDetailsService.loadUserByUsername("missing"))
        .isInstanceOf(UsernameNotFoundException.class)
        .hasMessageContaining("User not found: missing");
  }

  @Test
  @DisplayName("Authentication entry point should write the shared 401 JSON envelope")
  void restAuthenticationEntryPoint_shouldWriteUnauthorizedResponse() throws Exception {
    // Arrange
    ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    RestAuthenticationEntryPoint entryPoint = new RestAuthenticationEntryPoint(objectMapper);
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();

    // Act
    entryPoint.commence(request, response, new org.springframework.security.authentication.BadCredentialsException("bad"));
    JsonNode body = objectMapper.readTree(response.getContentAsString());

    // Assert
    assertThat(response.getStatus()).isEqualTo(401);
    assertThat(body.path("errorCode").asText()).isEqualTo(ErrorCode.UNAUTHORIZED.name());
  }

  @Test
  @DisplayName("Access denied handler should write the shared 403 JSON envelope")
  void restAccessDeniedHandler_shouldWriteForbiddenResponse() throws Exception {
    // Arrange
    ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    RestAccessDeniedHandler handler = new RestAccessDeniedHandler(objectMapper);
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();

    // Act
    handler.handle(request, response, new AccessDeniedException("forbidden"));
    JsonNode body = objectMapper.readTree(response.getContentAsString());

    // Assert
    assertThat(response.getStatus()).isEqualTo(403);
    assertThat(body.path("errorCode").asText()).isEqualTo(ErrorCode.FORBIDDEN.name());
  }

  @Test
  @DisplayName("JWT filter should continue the chain untouched when no bearer token is present")
  void jwtAuthenticationFilter_withoutBearerHeader_shouldContinueChain() throws Exception {
    // Arrange
    CustomUserDetailsService customUserDetailsService = org.mockito.Mockito.mock(CustomUserDetailsService.class);
    JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService, customUserDetailsService);
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain filterChain = new MockFilterChain();

    // Act
    filter.doFilter(request, response, filterChain);

    // Assert
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }

  @Test
  @DisplayName("JWT filter should install authentication when the bearer token is valid")
  void jwtAuthenticationFilter_withValidToken_shouldPopulateSecurityContext() throws Exception {
    // Arrange
    CustomUserDetailsService customUserDetailsService = org.mockito.Mockito.mock(CustomUserDetailsService.class);
    JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService, customUserDetailsService);
    CustomUserDetails principal = CustomUserDetails.fromUser(buildUser(true, null));
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("Authorization", "Bearer valid-token");
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain filterChain = new MockFilterChain();
    when(jwtService.extractUsername("valid-token")).thenReturn(principal.getUsername());
    when(customUserDetailsService.loadUserByUsername(principal.getUsername())).thenReturn(principal);
    when(jwtService.validateToken("valid-token", principal)).thenReturn(true);

    // Act
    filter.doFilter(request, response, filterChain);

    // Assert
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
    assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo(principal.getUsername());
  }

  @Test
  @DisplayName("JWT filter should ignore invalid tokens and still continue the request")
  void jwtAuthenticationFilter_withInvalidToken_shouldIgnoreAndContinue() throws Exception {
    // Arrange
    CustomUserDetailsService customUserDetailsService = org.mockito.Mockito.mock(CustomUserDetailsService.class);
    JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService, customUserDetailsService);
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("Authorization", "Bearer bad-token");
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain filterChain = new MockFilterChain();
    when(jwtService.extractUsername("bad-token")).thenThrow(new JwtException("bad token"));

    // Act
    filter.doFilter(request, response, filterChain);

    // Assert
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    verify(jwtService).extractUsername("bad-token");
  }

  private User buildUser(boolean active, LocalDateTime lockedUntil) {
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
    return user;
  }
}
