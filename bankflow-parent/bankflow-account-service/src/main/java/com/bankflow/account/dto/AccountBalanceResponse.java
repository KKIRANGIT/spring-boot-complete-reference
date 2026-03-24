package com.bankflow.account.dto;

import com.bankflow.common.domain.AccountStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Focused balance query payload.
 *
 * <p>Plain English: this response is intentionally smaller than the full account document so
 * frequent balance requests can stay light and cache-friendly.
 */
public record AccountBalanceResponse(
    UUID accountId,
    BigDecimal balance,
    String currency,
    AccountStatus status,
    LocalDateTime asOf) {
}
