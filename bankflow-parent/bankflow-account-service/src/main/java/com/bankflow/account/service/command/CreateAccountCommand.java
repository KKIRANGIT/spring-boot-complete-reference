package com.bankflow.account.service.command;

import com.bankflow.common.domain.AccountType;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * CQRS command for account creation.
 *
 * <p>Plain English: write-side code receives a command object instead of controller DTOs so the
 * domain service stays transport-agnostic.
 */
public record CreateAccountCommand(
    UUID userId,
    AccountType accountType,
    BigDecimal initialDeposit,
    String currency,
    UUID performedBy) {
}
