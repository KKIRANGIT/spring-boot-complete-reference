package com.bankflow.account;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Bootstrap entry point for the Account Service.
 */
@SpringBootApplication(scanBasePackages = "com.bankflow")
public class BankflowAccountServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(BankflowAccountServiceApplication.class, args);
  }
}
