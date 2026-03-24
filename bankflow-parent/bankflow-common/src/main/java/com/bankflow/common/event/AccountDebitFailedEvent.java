package com.bankflow.common.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Failure event emitted when account-service cannot debit the requested amount.
 *
 * <p>Plain English: payment-service consumes this to stop the saga and publish a failed payment.
 */
public record AccountDebitFailedEvent(
    UUID eventId,
    UUID transactionId,
    UUID accountId,
    BigDecimal amount,
    String reason,
    LocalDateTime occurredAt) {
}
