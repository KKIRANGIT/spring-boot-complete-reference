package com.bankflow.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for the BankFlow Payment Service.
 *
 * <p>Plain English: this boots the service responsible for transfer initiation, saga choreography,
 * outbox publishing, and idempotent payment request handling on port 8083.
 *
 * <p>Design decision: JPA auditing and scheduling are enabled centrally because payment-service
 * depends on created timestamps and a polling outbox publisher to solve the dual write problem.
 */
@EnableScheduling
@EnableJpaAuditing
@SpringBootApplication(scanBasePackages = "com.bankflow")
public class BankflowPaymentServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(BankflowPaymentServiceApplication.class, args);
  }
}
