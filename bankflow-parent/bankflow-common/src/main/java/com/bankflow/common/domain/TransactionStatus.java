package com.bankflow.common.domain;

/**
 * Lifecycle states for a payment or ledger transaction.
 *
 * <p>Design decision: explicit transaction states make long-running workflows and reconciliation
 * easier to reason about than boolean flags such as processed/not processed.
 *
 * <p>Interview answer: this class shows how to model business workflows with state transitions.
 *
 * <p>Bug prevented: vague status fields make it difficult to distinguish in-flight work from work
 * that failed and already needs compensation.
 */
public enum TransactionStatus {
  PENDING,
  PROCESSING,
  COMPLETED,
  FAILED,
  REVERSED
}
