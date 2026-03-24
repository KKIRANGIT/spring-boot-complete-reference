package com.bankflow.payment.domain;

/**
 * Delivery lifecycle for payment-service outbox rows.
 *
 * <p>Plain English: outbox rows start pending, move to published after Kafka confirms receipt, and
 * become failed after repeated retry exhaustion.
 */
public enum OutboxStatus {
  PENDING,
  PUBLISHED,
  FAILED
}
