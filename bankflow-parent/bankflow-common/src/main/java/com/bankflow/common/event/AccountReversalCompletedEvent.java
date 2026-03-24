package com.bankflow.common.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Event emitted when account-service completes a compensation credit.
 *
 * <p>Plain English: payment-service uses this to mark a failed payment as reversed cleanly.
 */
public record AccountReversalCompletedEvent(
    UUID eventId,
    UUID transactionId,
    UUID accountId,
    BigDecimal amount,
    BigDecimal newBalance,
    LocalDateTime occurredAt) {
}
