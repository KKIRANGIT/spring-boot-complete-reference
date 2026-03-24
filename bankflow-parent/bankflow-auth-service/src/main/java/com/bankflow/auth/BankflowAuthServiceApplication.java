package com.bankflow.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Entry point for the BankFlow Auth Service.
 *
 * <p>Plain English: this boots the registration, login, JWT, refresh-token, and logout service on
 * port 8081.
 *
 * <p>Design decision: JPA auditing is enabled here so entity timestamps such as {@code createdAt}
 * and {@code updatedAt} are filled automatically instead of manually in every service method.
 *
 * <p>Bug prevented: without auditing, timestamp fields are easy to forget during updates and
 * create inconsistent audit trails.
 *
 * <p>Interview question answered: "How do you bootstrap a Spring Boot microservice with automatic
 * persistence auditing?"
 */
@EnableJpaAuditing
@SpringBootApplication(scanBasePackages = "com.bankflow")
public class BankflowAuthServiceApplication {

  /**
   * Starts the Auth Service JVM process.
   *
   * <p>Plain English: this method hands control to Spring Boot so it can create the web server,
   * database connections, Redis client, security filters, and controllers.
   *
   * <p>Interview question answered: "What does the main class do in a Spring Boot microservice?"
   */
  public static void main(String[] args) {
    SpringApplication.run(BankflowAuthServiceApplication.class, args);
  }
}
