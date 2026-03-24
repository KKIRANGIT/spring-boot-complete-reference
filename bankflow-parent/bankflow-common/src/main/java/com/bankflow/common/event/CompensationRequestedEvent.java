package com.bankflow.common.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Compensation event that instructs account-service to reverse an earlier debit.
 *
 * <p>Plain English: this is how payment-service tells account-service to refund a failed transfer.
 *
 * <p>Bug prevented: keeping reversal events explicit avoids hidden rollback logic that is hard to
 * audit in a distributed system.
 */
public record CompensationRequestedEvent(
    UUID eventId,
    UUID transactionId,
    UUID accountId,
    BigDecimal amount,
    String reason,
    LocalDateTime occurredAt) {
}
