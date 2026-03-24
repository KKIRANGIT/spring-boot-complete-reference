package com.bankflow.common.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Payment saga start event published when a distributed payment flow begins.
 *
 * <p>Plain English: account-service consumes this event to debit the source account.
 *
 * <p>Design decision: event id and transaction id are separated because idempotency belongs to the
 * event delivery while reconciliation belongs to the business transaction itself.
 *
 * <p>Interview question answered: "What fields do you include in a Kafka event for a payment saga?"
 */
public record PaymentInitiatedEvent(
    UUID eventId,
    UUID transactionId,
    UUID fromAccountId,
    UUID toAccountId,
    BigDecimal amount,
    String currency,
    LocalDateTime occurredAt) {
}
