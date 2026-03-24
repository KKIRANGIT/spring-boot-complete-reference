package com.bankflow.common.event;

import com.bankflow.common.domain.AccountStatus;
import com.bankflow.common.domain.AccountType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Event published after a new account is created successfully.
 *
 * <p>Plain English: downstream services can react to new accounts without querying the account
 * database directly.
 */
public record AccountCreatedEvent(
    UUID eventId,
    UUID accountId,
    UUID userId,
    String accountNumber,
    AccountType accountType,
    BigDecimal balance,
    String currency,
    AccountStatus status,
    LocalDateTime occurredAt) {
}
