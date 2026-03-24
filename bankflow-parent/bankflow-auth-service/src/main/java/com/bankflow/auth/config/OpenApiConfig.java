package com.bankflow.auth.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI configuration for Swagger UI.
 *
 * <p>Plain English: this defines the API title and the bearer-token scheme used by secured auth
 * endpoints in Swagger.
 *
 * <p>Interview question answered: "How do you document JWT security in OpenAPI?"
 */
@Configuration
public class OpenApiConfig {

  /** Builds the Auth Service OpenAPI definition. */
  @Bean
  public OpenAPI authServiceOpenApi() {
    final String schemeName = "bearerAuth";

    return new OpenAPI()
        .info(new Info()
            .title("BankFlow Auth Service API")
            .version("v1")
            .description("Registration, login, JWT issuance, refresh rotation, logout, and profile APIs."))
        .addSecurityItem(new SecurityRequirement().addList(schemeName))
        .components(new Components().addSecuritySchemes(
            schemeName,
            new SecurityScheme()
                .name(schemeName)
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")));
  }
}
