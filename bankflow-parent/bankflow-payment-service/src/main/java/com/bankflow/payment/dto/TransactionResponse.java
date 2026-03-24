package com.bankflow.payment.dto;

import com.bankflow.common.domain.SagaStatus;
import com.bankflow.common.domain.TransactionStatus;
import com.bankflow.common.domain.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Query response for a stored payment transaction.
 */
public record TransactionResponse(
    UUID id,
    String transactionReference,
    UUID fromAccountId,
    UUID toAccountId,
    BigDecimal amount,
    String currency,
    TransactionType type,
    TransactionStatus status,
    SagaStatus sagaStatus,
    String description,
    String failureReason,
    LocalDateTime createdAt,
    LocalDateTime completedAt) {
}
