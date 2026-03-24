package com.bankflow.common.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Saga step requesting a credit into the destination account after the source debit succeeds.
 *
 * <p>Plain English: payment-service publishes this event after it confirms the sender account was
 * debited, and account-service consumes it to credit the receiver account.
 *
 * <p>Interview question answered: "How do you model the second half of a transfer in a choreography
 * saga without making one service call another directly?"
 */
public record AccountCreditRequestedEvent(
    UUID eventId,
    UUID transactionId,
    UUID toAccountId,
    BigDecimal amount,
    String currency,
    LocalDateTime occurredAt) {
}
