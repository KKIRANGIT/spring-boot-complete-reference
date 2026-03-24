package com.bankflow.payment.service;

import com.bankflow.common.domain.SagaStatus;
import com.bankflow.common.domain.TransactionStatus;
import com.bankflow.common.domain.TransactionType;
import com.bankflow.common.event.PaymentInitiatedEvent;
import com.bankflow.common.kafka.KafkaTopics;
import com.bankflow.payment.dto.TransferRequest;
import com.bankflow.payment.entity.Transaction;
import com.bankflow.payment.repository.OutboxEventRepository;
import com.bankflow.payment.repository.TransactionRepository;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transactional writer for the transfer row plus its matching outbox row.
 *
 * <p>Plain English: this bean exists so the HTTP-facing payment service can cache the response only
 * after the database commit succeeds.
 */
@Service
public class PaymentTransactionWriter {

  private final TransactionRepository transactionRepository;
  private final OutboxEventRepository outboxEventRepository;
  private final TransactionReferenceGenerator transactionReferenceGenerator;
  private final OutboxEventFactory outboxEventFactory;

  public PaymentTransactionWriter(
      TransactionRepository transactionRepository,
      OutboxEventRepository outboxEventRepository,
      TransactionReferenceGenerator transactionReferenceGenerator,
      OutboxEventFactory outboxEventFactory) {
    this.transactionRepository = transactionRepository;
    this.outboxEventRepository = outboxEventRepository;
    this.transactionReferenceGenerator = transactionReferenceGenerator;
    this.outboxEventFactory = outboxEventFactory;
  }

  @Transactional
  public Transaction createPendingTransfer(TransferRequest request, String idempotencyKey) {
    Transaction transaction = new Transaction();
    transaction.setId(UUID.randomUUID());
    transaction.setTransactionReference(transactionReferenceGenerator.generate());
    transaction.setIdempotencyKey(idempotencyKey);
    transaction.setFromAccountId(request.fromAccountId());
    transaction.setToAccountId(request.toAccountId());
    transaction.setAmount(request.amount());
    transaction.setCurrency(normalizeCurrency(request.currency()));
    transaction.setType(TransactionType.TRANSFER);
    transaction.setStatus(TransactionStatus.PENDING);
    transaction.setSagaStatus(SagaStatus.STARTED);
    transaction.setDescription(request.description());

    Transaction savedTransaction = transactionRepository.saveAndFlush(transaction);

    PaymentInitiatedEvent paymentInitiatedEvent = new PaymentInitiatedEvent(
        UUID.randomUUID(),
        savedTransaction.getId(),
        savedTransaction.getFromAccountId(),
        savedTransaction.getToAccountId(),
        savedTransaction.getAmount(),
        savedTransaction.getCurrency(),
        LocalDateTime.now());

    outboxEventRepository.save(outboxEventFactory.createTransactionOutboxEvent(
        savedTransaction.getId(),
        KafkaTopics.PAYMENT_INITIATED,
        paymentInitiatedEvent));

    return savedTransaction;
  }

  private String normalizeCurrency(String currency) {
    return currency == null || currency.isBlank()
        ? "INR"
        : currency.trim().toUpperCase(Locale.ROOT);
  }
}
