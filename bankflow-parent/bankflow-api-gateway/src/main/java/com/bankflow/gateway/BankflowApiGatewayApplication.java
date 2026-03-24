package com.bankflow.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Bootstrap entry point for the API Gateway.
 */
@SpringBootApplication(scanBasePackages = "com.bankflow")
public class BankflowApiGatewayApplication {

  public static void main(String[] args) {
    SpringApplication.run(BankflowApiGatewayApplication.class, args);
  }
}
