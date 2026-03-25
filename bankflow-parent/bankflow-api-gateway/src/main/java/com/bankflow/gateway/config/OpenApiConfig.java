package com.bankflow.gateway.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI configuration for the reactive API gateway.
 *
 * <p>Plain English: the gateway is the public edge of BankFlow, so its OpenAPI document explains
 * the bearer-token contract used by clients before requests are routed to downstream services.
 */
@Configuration
public class OpenApiConfig {

  /**
   * Creates the OpenAPI definition exposed by the API gateway.
   */
  @Bean
  public OpenAPI gatewayOpenApi() {
    String schemeName = "bearerAuth";

    return new OpenAPI()
        .info(new Info()
            .title("BankFlow API Gateway")
            .version("v1")
            .description("Gateway entrypoint for authentication, rate limiting, circuit breaking, and fallback handling."))
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
