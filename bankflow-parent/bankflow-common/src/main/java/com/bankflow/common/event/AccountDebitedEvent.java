package com.bankflow.common.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Event emitted after money is successfully debited from an account.
 *
 * <p>Plain English: payment-service listens for this to continue the transfer saga.
 */
public record AccountDebitedEvent(
    UUID eventId,
    UUID transactionId,
    UUID accountId,
    BigDecimal amount,
    BigDecimal newBalance,
    LocalDateTime occurredAt) {
}
