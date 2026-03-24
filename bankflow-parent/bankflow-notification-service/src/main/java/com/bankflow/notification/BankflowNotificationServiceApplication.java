package com.bankflow.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Bootstrap entry point for the Notification Service.
 */
@SpringBootApplication(scanBasePackages = "com.bankflow")
public class BankflowNotificationServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(BankflowNotificationServiceApplication.class, args);
  }
}
