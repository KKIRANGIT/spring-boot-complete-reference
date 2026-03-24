package com.bankflow.common.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Final event emitted when a transfer finishes successfully.
 *
 * <p>Plain English: downstream services such as notification-service can consume this to tell the
 * user that the transfer is complete and funds reached the destination account.
 */
public record PaymentCompletedEvent(
    UUID eventId,
    UUID transactionId,
    String transactionReference,
    UUID fromAccountId,
    UUID toAccountId,
    BigDecimal amount,
    String currency,
    LocalDateTime occurredAt) {
}
