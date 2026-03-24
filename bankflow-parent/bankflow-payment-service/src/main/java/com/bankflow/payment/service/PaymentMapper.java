package com.bankflow.payment.service;

import com.bankflow.payment.dto.TransactionResponse;
import com.bankflow.payment.dto.TransferResponse;
import com.bankflow.payment.entity.Transaction;
import org.springframework.stereotype.Component;

/**
 * Maps payment entities into outward-facing response DTOs.
 */
@Component
public class PaymentMapper {

  public TransferResponse toTransferResponse(Transaction transaction) {
    return new TransferResponse(
        transaction.getId(),
        transaction.getTransactionReference(),
        transaction.getStatus(),
        transaction.getSagaStatus(),
        transaction.getAmount(),
        transaction.getCurrency(),
        transaction.getCreatedAt());
  }

  public TransactionResponse toTransactionResponse(Transaction transaction) {
    return new TransactionResponse(
        transaction.getId(),
        transaction.getTransactionReference(),
        transaction.getFromAccountId(),
        transaction.getToAccountId(),
        transaction.getAmount(),
        transaction.getCurrency(),
        transaction.getType(),
        transaction.getStatus(),
        transaction.getSagaStatus(),
        transaction.getDescription(),
        transaction.getFailureReason(),
        transaction.getCreatedAt(),
        transaction.getCompletedAt());
  }
}
