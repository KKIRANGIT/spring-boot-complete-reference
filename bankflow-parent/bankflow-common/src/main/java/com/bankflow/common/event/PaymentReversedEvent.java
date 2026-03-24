package com.bankflow.common.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Final event emitted when a transfer had to be compensated and the sender's money was restored.
 *
 * <p>Plain English: this is the audit-friendly signal that the saga could not complete forward but
 * did complete its reversal correctly.
 */
public record PaymentReversedEvent(
    UUID eventId,
    UUID transactionId,
    String transactionReference,
    UUID fromAccountId,
    UUID toAccountId,
    BigDecimal amount,
    String currency,
    LocalDateTime occurredAt) {
}
