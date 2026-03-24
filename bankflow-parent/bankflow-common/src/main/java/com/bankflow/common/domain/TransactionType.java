package com.bankflow.common.domain;

/**
 * Types of account-affecting transactions supported by BankFlow.
 *
 * <p>Design decision: transaction type is normalized as an enum so downstream event handlers and
 * ledger logic can branch consistently.
 *
 * <p>Interview answer: this class shows how domain vocabulary is centralized for maintainability.
 *
 * <p>Bug prevented: textual transaction kinds drift quickly between services and reports when they
 * are not codified in a shared module.
 */
public enum TransactionType {
  DEBIT,
  CREDIT,
  TRANSFER,
  REVERSAL
}
