package com.bankflow.auth;

import static org.mockito.Mockito.mockStatic;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.boot.SpringApplication;

/**
 * Smoke test for the Spring Boot application entrypoint.
 *
 * <p>Plain English: this proves the main method delegates to {@link SpringApplication#run} with the
 * correct application class, which prevents silent breakage during refactors.
 */
class BankflowAuthServiceApplicationTest {

  @Test
  @DisplayName("Main method should delegate to SpringApplication.run with the auth service class")
  void main_shouldDelegateToSpringApplication() {
    // Arrange
    String[] args = {"--spring.profiles.active=test"};

    // Act + Assert
    try (MockedStatic<SpringApplication> springApplication = mockStatic(SpringApplication.class)) {
      BankflowAuthServiceApplication.main(args);
      springApplication.verify(() -> SpringApplication.run(BankflowAuthServiceApplication.class, args));
    }
  }
}
