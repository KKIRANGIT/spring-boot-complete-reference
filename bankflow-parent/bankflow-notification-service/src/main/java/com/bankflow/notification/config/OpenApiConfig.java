package com.bankflow.notification.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI configuration for notification-service.
 *
 * <p>Plain English: this service is primarily event-driven and consumes Kafka topics instead of
 * exposing business REST endpoints, so the OpenAPI page is used mainly to describe the service role
 * and document that only operational endpoints such as Actuator are expected here.
 */
@Configuration
public class OpenApiConfig {

  /**
   * Creates the OpenAPI definition for notification-service.
   */
  @Bean
  public OpenAPI notificationServiceOpenApi() {
    return new OpenAPI()
        .info(new Info()
            .title("BankFlow Notification Service API")
            .version("v1")
            .description("Event-driven notification service that consumes Kafka events, logs delivery outcomes, and sends local email through MailHog. This service intentionally has no public business REST endpoints."));
  }
}
