package com.bankflow.payment.service;

import java.security.SecureRandom;
import java.time.Instant;
import org.springframework.stereotype.Component;

/**
 * Generates user-facing payment references like `TXN17100000000001234`.
 *
 * <p>Plain English: the reference is readable enough for support teams and users while still being
 * detached from internal database identifiers.
 */
@Component
public class TransactionReferenceGenerator {

  private final SecureRandom secureRandom = new SecureRandom();

  public String generate() {
    long epochMillis = Instant.now().toEpochMilli();
    int randomSuffix = secureRandom.nextInt(10_000);
    return "TXN" + epochMillis + String.format("%04d", randomSuffix);
  }
}
