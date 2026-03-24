package com.bankflow.payment.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI configuration for payment-service.
 */
@Configuration
public class OpenApiConfig {

  @Bean
  public OpenAPI paymentServiceOpenApi() {
    String schemeName = "gatewayHeaders";

    return new OpenAPI()
        .info(new Info()
            .title("BankFlow Payment Service API")
            .version("v1")
            .description("Transfer initiation, saga choreography, outbox publishing, and idempotency."))
        .addSecurityItem(new SecurityRequirement().addList(schemeName))
        .components(new Components().addSecuritySchemes(
            schemeName,
            new SecurityScheme()
                .name("X-User-Id / X-User-Roles")
                .type(SecurityScheme.Type.APIKEY)
                .in(SecurityScheme.In.HEADER)));
  }
}
