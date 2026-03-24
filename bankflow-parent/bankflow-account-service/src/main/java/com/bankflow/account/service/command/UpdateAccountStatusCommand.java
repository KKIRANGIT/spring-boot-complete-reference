package com.bankflow.account.service.command;

import com.bankflow.common.domain.AccountStatus;
import java.util.UUID;

/**
 * CQRS command for changing account status.
 */
public record UpdateAccountStatusCommand(
    UUID accountId,
    AccountStatus status,
    UUID performedBy,
    String description) {
}
