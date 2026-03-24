package com.bankflow.common.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Event emitted after money is credited to an account.
 *
 * <p>Plain English: this supports compensations and future inbound-funds workflows.
 */
public record AccountCreditedEvent(
    UUID eventId,
    UUID transactionId,
    UUID accountId,
    BigDecimal amount,
    BigDecimal newBalance,
    LocalDateTime occurredAt) {
}
