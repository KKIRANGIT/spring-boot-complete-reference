package com.bankflow.common.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Failure event emitted when the destination account could not be credited.
 *
 * <p>Plain English: payment-service consumes this to start compensation and return funds to the
 * sender because the forward saga path cannot complete.
 */
public record AccountCreditFailedEvent(
    UUID eventId,
    UUID transactionId,
    UUID toAccountId,
    BigDecimal amount,
    String reason,
    LocalDateTime occurredAt) {
}
