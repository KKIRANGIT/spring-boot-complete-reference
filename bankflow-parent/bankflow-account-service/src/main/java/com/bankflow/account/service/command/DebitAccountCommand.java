package com.bankflow.account.service.command;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * CQRS command for debiting an account.
 */
public record DebitAccountCommand(
    UUID accountId,
    BigDecimal amount,
    UUID transactionId,
    UUID performedBy,
    String description) {
}
