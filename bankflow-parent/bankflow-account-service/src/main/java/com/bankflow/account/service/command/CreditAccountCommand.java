package com.bankflow.account.service.command;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * CQRS command for crediting an account.
 */
public record CreditAccountCommand(
    UUID accountId,
    BigDecimal amount,
    UUID transactionId,
    UUID performedBy,
    String description) {
}
