package com.bankflow.account.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI configuration for account-service.
 *
 * <p>Plain English: Swagger documents that internal gateway headers represent the authenticated
 * user context seen by this service.
 */
@Configuration
public class OpenApiConfig {

  /**
   * Creates the OpenAPI model for account-service.
   */
  @Bean
  public OpenAPI accountServiceOpenApi() {
    String schemeName = "gatewayHeaders";

    return new OpenAPI()
        .info(new Info()
            .title("BankFlow Account Service API")
            .version("v1")
            .description("CQRS account creation, balance queries, statements, and saga endpoints."))
        .addSecurityItem(new SecurityRequirement().addList(schemeName))
        .components(new Components().addSecuritySchemes(
            schemeName,
            new SecurityScheme()
                .name("X-User-Id / X-User-Roles")
                .type(SecurityScheme.Type.APIKEY)
                .in(SecurityScheme.In.HEADER)));
  }
}
