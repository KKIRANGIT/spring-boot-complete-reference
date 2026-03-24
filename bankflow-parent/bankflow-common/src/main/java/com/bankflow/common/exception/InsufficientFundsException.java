package com.bankflow.common.exception;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Raised when a debit or transfer exceeds the available account balance.
 *
 * <p>Design decision: the message includes account id, requested amount, and available amount so
 * operators can debug financial failures without reproducing the transaction manually.
 *
 * <p>Interview answer: this class shows how to make domain failures observable without leaking
 * stack traces to API consumers.
 *
 * <p>Bug prevented: hiding the financial context makes reconciliation and incident analysis much
 * harder when distributed steps fail mid-saga.
 */
public class InsufficientFundsException extends RuntimeException {

  public InsufficientFundsException(UUID accountId, BigDecimal requested, BigDecimal available) {
    super("Insufficient funds for account " + accountId + ". Requested: " + requested
        + ", available: " + available);
  }
}
