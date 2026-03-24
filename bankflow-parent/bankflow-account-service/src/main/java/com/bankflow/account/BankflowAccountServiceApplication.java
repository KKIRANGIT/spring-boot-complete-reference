package com.bankflow.account;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.retry.annotation.EnableRetry;

/**
 * Entry point for the BankFlow Account Service.
 *
 * <p>Plain English: this boots the service responsible for account creation, balance mutation,
 * statements, caching, and account-side payment saga processing on port 8082.
 *
 * <p>Design decision: JPA auditing, caching, and retry support are enabled centrally because those
 * are first-class concerns in a banking write service, not optional add-ons.
 *
 * <p>Interview question answered: "How do you bootstrap a Spring Boot account service that needs
 * optimistic-lock retries, Redis caching, and audit timestamps?"
 */
@EnableRetry
@EnableCaching
@EnableJpaAuditing
@SpringBootApplication(scanBasePackages = "com.bankflow")
public class BankflowAccountServiceApplication {

  /**
   * Starts the account-service JVM.
   */
  public static void main(String[] args) {
    SpringApplication.run(BankflowAccountServiceApplication.class, args);
  }
}
