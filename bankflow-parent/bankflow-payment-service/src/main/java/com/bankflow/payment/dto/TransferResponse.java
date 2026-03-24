package com.bankflow.payment.dto;

import com.bankflow.common.domain.SagaStatus;
import com.bankflow.common.domain.TransactionStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Transfer initiation response cached for idempotent replay.
 *
 * <p>Plain English: payment-service returns this exact payload for the original request and any
 * duplicate request with the same idempotency key.
 */
public record TransferResponse(
    UUID transactionId,
    String transactionReference,
    TransactionStatus status,
    SagaStatus sagaStatus,
    BigDecimal amount,
    String currency,
    LocalDateTime createdAt) {
}
