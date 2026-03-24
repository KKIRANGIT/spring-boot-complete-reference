package com.bankflow.common.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Final event emitted when a transfer fails before crediting the receiver.
 *
 * <p>Plain English: this lets downstream services tell the user why the payment failed without
 * polling the payment-service database.
 */
public record PaymentFailedEvent(
    UUID eventId,
    UUID transactionId,
    String transactionReference,
    UUID fromAccountId,
    UUID toAccountId,
    BigDecimal amount,
    String currency,
    String reason,
    LocalDateTime occurredAt) {
}
