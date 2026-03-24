package com.bankflow.auth.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link OpenApiConfig}.
 *
 * <p>Plain English: this verifies the generated OpenAPI definition keeps the expected title and JWT
 * bearer security scheme that Swagger UI relies on.
 */
class OpenApiConfigTest {

  @Test
  @DisplayName("OpenAPI configuration should publish the auth service title and bearer scheme")
  void authServiceOpenApi_shouldExposeBearerSecurityScheme() {
    // Arrange
    OpenApiConfig openApiConfig = new OpenApiConfig();

    // Act
    OpenAPI openAPI = openApiConfig.authServiceOpenApi();

    // Assert
    assertThat(openAPI.getInfo().getTitle()).isEqualTo("BankFlow Auth Service API");
    assertThat(openAPI.getInfo().getVersion()).isEqualTo("v1");
    assertThat(openAPI.getComponents().getSecuritySchemes()).containsKey("bearerAuth");
    assertThat(openAPI.getComponents().getSecuritySchemes().get("bearerAuth").getScheme()).isEqualTo("bearer");
    assertThat(openAPI.getSecurity()).hasSize(1);
  }
}
