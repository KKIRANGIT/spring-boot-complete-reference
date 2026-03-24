package com.bankflow.account.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Read model for one statement or audit-log line.
 *
 * <p>Plain English: this tells the user or auditor what action happened and how the balance moved.
 */
public record AccountStatementEntryResponse(
    UUID id,
    String action,
    BigDecimal previousBalance,
    BigDecimal newBalance,
    BigDecimal amount,
    UUID performedBy,
    LocalDateTime performedAt,
    String description) {
}
