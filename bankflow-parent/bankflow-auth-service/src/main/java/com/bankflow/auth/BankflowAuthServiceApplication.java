package com.bankflow.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Bootstrap entry point for the Auth Service.
 *
 * <p>The scan base package includes {@code com.bankflow} so shared advice and utilities from the
 * common module are discovered automatically by every service.
 */
@SpringBootApplication(scanBasePackages = "com.bankflow")
public class BankflowAuthServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(BankflowAuthServiceApplication.class, args);
  }
}
