package com.bankflow.account.dto;

import com.bankflow.common.domain.AccountStatus;
import com.bankflow.common.domain.AccountType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Query-model payload for account details.
 *
 * <p>Plain English: this is what the UI reads for account profile pages and account listings.
 */
public record AccountResponse(
    UUID id,
    String accountNumber,
    UUID userId,
    AccountType accountType,
    BigDecimal balance,
    String currency,
    AccountStatus status,
    LocalDateTime createdAt,
    LocalDateTime updatedAt) {
}
