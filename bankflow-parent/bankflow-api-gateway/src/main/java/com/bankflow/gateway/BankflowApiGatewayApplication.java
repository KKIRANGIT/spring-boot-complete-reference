package com.bankflow.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the BankFlow API Gateway.
 *
 * <p>Plain English: this service is the single internet-facing edge for BankFlow. It validates
 * JWTs once, adds trusted identity and correlation headers, rate-limits callers, and routes
 * traffic to internal microservices.
 */
@SpringBootApplication(scanBasePackages = "com.bankflow")
public class BankflowApiGatewayApplication {

  public static void main(String[] args) {
    SpringApplication.run(BankflowApiGatewayApplication.class, args);
  }
}
