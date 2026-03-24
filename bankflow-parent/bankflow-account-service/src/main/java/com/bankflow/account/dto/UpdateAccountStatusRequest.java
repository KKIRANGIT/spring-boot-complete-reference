package com.bankflow.account.dto;

import com.bankflow.common.domain.AccountStatus;
import jakarta.validation.constraints.NotNull;

/**
 * Admin-only request for changing an account's lifecycle state.
 */
public record UpdateAccountStatusRequest(
    @NotNull AccountStatus status,
    String description) {
}
