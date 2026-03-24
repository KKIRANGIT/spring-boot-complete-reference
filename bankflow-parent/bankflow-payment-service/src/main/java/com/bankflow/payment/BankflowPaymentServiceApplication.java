package com.bankflow.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Bootstrap entry point for the Payment Service.
 */
@SpringBootApplication(scanBasePackages = "com.bankflow")
public class BankflowPaymentServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(BankflowPaymentServiceApplication.class, args);
  }
}
