package com.bankflow.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.bankflow.auth.dto.LoginRequest;
import com.bankflow.auth.dto.RefreshTokenRequest;
import com.bankflow.auth.dto.RegisterRequest;
import com.bankflow.auth.entity.RefreshToken;
import com.bankflow.auth.repository.RefreshTokenRepository;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Integration tests for the auth HTTP API against real MySQL and Redis containers.
 *
 * <p>Plain English: these tests boot the entire auth service and prove that controllers, security,
 * JPA, JWT handling, refresh-token rotation, and Redis blacklisting work together end to end.
 *
 * <p>Design decision: real infrastructure is used here because token rotation and logout behavior
 * are only trustworthy when persistence and Redis are part of the execution path.
 *
 * <p>Bug prevented: mock-only tests cannot catch configuration mismatches between JPA, Redis,
 * Spring Security, and controller serialization.
 *
 * <p>Interview question answered: "How do you integration test a Spring Boot auth service with
 * MySQL, Redis, and JWT security?"
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
class AuthControllerIT {

  @Container
  static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.0.36"))
      .withDatabaseName("bankflow_auth")
      .withUsername("bankflow")
      .withPassword("bankflow");

  @Container
  static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
      .withCommand("redis-server", "--requirepass", "bankflow_redis")
      .withExposedPorts(6379);

  @DynamicPropertySource
  static void configureProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
    registry.add("spring.datasource.username", MYSQL::getUsername);
    registry.add("spring.datasource.password", MYSQL::getPassword);
    registry.add("spring.data.redis.host", REDIS::getHost);
    registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    registry.add("spring.data.redis.password", () -> "bankflow_redis");
  }

  @LocalServerPort
  private int port;

  @Autowired
  private TestRestTemplate restTemplate;

  @Autowired
  private RefreshTokenRepository refreshTokenRepository;

  @Test
  @DisplayName("Registration, login, profile fetch, and logout should work end to end")
  void fullRegistrationLoginLogoutFlow() {
    // Arrange
    String unique = UUID.randomUUID().toString().substring(0, 8);
    String username = "user_" + unique;
    String email = unique + "@bankflow.local";
    RegisterRequest registerRequest = new RegisterRequest(username, email, "Password1!");

    // Act
    ResponseEntity<JsonNode> registerResponse = post("/api/v1/auth/register", registerRequest, jsonHeaders());
    ResponseEntity<JsonNode> loginResponse =
        post("/api/v1/auth/login", new LoginRequest(username, "Password1!"), jsonHeaders());
    String accessToken = loginResponse.getBody().path("data").path("accessToken").asText();
    String refreshToken = loginResponse.getBody().path("data").path("refreshToken").asText();
    ResponseEntity<JsonNode> meResponse = get("/api/v1/auth/me", bearerHeaders(accessToken));
    ResponseEntity<JsonNode> logoutResponse = post("/api/v1/auth/logout", null, bearerHeaders(accessToken));
    ResponseEntity<JsonNode> meAfterLogoutResponse = get("/api/v1/auth/me", bearerHeaders(accessToken));

    // Assert
    // This catches regressions where registration succeeds but the saved user cannot authenticate.
    assertThat(registerResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(accessToken).isNotBlank();
    assertThat(refreshToken).isNotBlank();
    assertThat(meResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(meResponse.getBody().path("data").path("username").asText()).isEqualTo(username);
    assertThat(logoutResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    // This is the critical end-to-end proof that Redis blacklisting revokes the JWT immediately.
    assertThat(meAfterLogoutResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  @DisplayName("Refresh token rotation should revoke the old refresh token and issue a new access token")
  void refreshTokenRotationFlow() {
    // Arrange
    String unique = UUID.randomUUID().toString().substring(0, 8);
    String username = "refresh_" + unique;
    String email = unique + "@bankflow.local";
    post("/api/v1/auth/register", new RegisterRequest(username, email, "Password1!"), jsonHeaders());
    ResponseEntity<JsonNode> loginResponse =
        post("/api/v1/auth/login", new LoginRequest(username, "Password1!"), jsonHeaders());
    String oldRefreshToken = loginResponse.getBody().path("data").path("refreshToken").asText();

    // Act
    ResponseEntity<JsonNode> refreshResponse =
        post("/api/v1/auth/refresh-token", new RefreshTokenRequest(oldRefreshToken), jsonHeaders());
    String newAccessToken = refreshResponse.getBody().path("data").path("accessToken").asText();
    ResponseEntity<JsonNode> meResponse = get("/api/v1/auth/me", bearerHeaders(newAccessToken));
    RefreshToken persistedOldToken = refreshTokenRepository.findByToken(oldRefreshToken).orElseThrow();
    ResponseEntity<JsonNode> oldRefreshReuseResponse =
        post("/api/v1/auth/refresh-token", new RefreshTokenRequest(oldRefreshToken), jsonHeaders());

    // Assert
    // This catches the security bug where refresh endpoints mint new tokens but forget to revoke the old one.
    assertThat(refreshResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(newAccessToken).isNotBlank();
    assertThat(meResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(persistedOldToken.isRevoked()).isTrue();
    assertThat(oldRefreshReuseResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  @DisplayName("Five wrong passwords should lock the account and keep it locked on the next attempt")
  void accountLockoutFlow() {
    // Arrange
    String unique = UUID.randomUUID().toString().substring(0, 8);
    String username = "locked_" + unique;
    String email = unique + "@bankflow.local";
    post("/api/v1/auth/register", new RegisterRequest(username, email, "Password1!"), jsonHeaders());
    LoginRequest wrongPasswordRequest = new LoginRequest(username, "WrongPassword1!");
    ResponseEntity<JsonNode> fifthAttemptResponse = null;
    ResponseEntity<JsonNode> sixthAttemptResponse;

    // Act
    for (int attempt = 1; attempt <= 5; attempt++) {
      ResponseEntity<JsonNode> response = post("/api/v1/auth/login", wrongPasswordRequest, jsonHeaders());
      if (attempt < 5) {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
      } else {
        fifthAttemptResponse = response;
      }
    }
    sixthAttemptResponse = post("/api/v1/auth/login", wrongPasswordRequest, jsonHeaders());

    // Assert
    // This catches regressions where the service counts failures but forgets to switch to 423 lockout responses.
    assertThat(fifthAttemptResponse).isNotNull();
    assertThat(fifthAttemptResponse.getStatusCode()).isEqualTo(HttpStatus.LOCKED);
    assertThat(sixthAttemptResponse.getStatusCode()).isEqualTo(HttpStatus.LOCKED);
  }

  private ResponseEntity<JsonNode> post(String path, Object body, HttpHeaders headers) {
    return restTemplate.exchange(
        url(path),
        HttpMethod.POST,
        new HttpEntity<>(body, headers),
        JsonNode.class);
  }

  private ResponseEntity<JsonNode> get(String path, HttpHeaders headers) {
    return restTemplate.exchange(
        url(path),
        HttpMethod.GET,
        new HttpEntity<>(headers),
        JsonNode.class);
  }

  private HttpHeaders jsonHeaders() {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
    return headers;
  }

  private HttpHeaders bearerHeaders(String accessToken) {
    HttpHeaders headers = jsonHeaders();
    headers.setBearerAuth(accessToken);
    return headers;
  }

  private String url(String path) {
    return "http://localhost:" + port + path;
  }
}
