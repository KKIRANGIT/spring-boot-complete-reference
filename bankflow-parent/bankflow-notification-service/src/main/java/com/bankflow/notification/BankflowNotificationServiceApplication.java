package com.bankflow.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Entry point for notification-service.
 *
 * <p>Plain English: this boots the service that consumes payment and account events, sends emails
 * through MailHog in local development, and stores a durable notification audit trail in MySQL.
 *
 * <p>Design decision: JPA auditing is enabled centrally because notification logs need a trusted
 * server-side created timestamp even when messages are redelivered by Kafka later.
 */
@EnableJpaAuditing
@SpringBootApplication(scanBasePackages = "com.bankflow")
public class BankflowNotificationServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(BankflowNotificationServiceApplication.class, args);
  }
}
