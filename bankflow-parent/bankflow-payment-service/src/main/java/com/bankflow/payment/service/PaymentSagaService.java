package com.bankflow.payment.service;

import com.bankflow.common.domain.SagaStatus;
import com.bankflow.common.domain.TransactionStatus;
import com.bankflow.common.event.AccountCreditedEvent;
import com.bankflow.common.event.AccountCreditFailedEvent;
import com.bankflow.common.event.AccountCreditRequestedEvent;
import com.bankflow.common.event.AccountDebitFailedEvent;
import com.bankflow.common.event.AccountDebitedEvent;
import com.bankflow.common.event.AccountReversalCompletedEvent;
import com.bankflow.common.event.CompensationRequestedEvent;
import com.bankflow.common.event.PaymentCompletedEvent;
import com.bankflow.common.event.PaymentFailedEvent;
import com.bankflow.common.event.PaymentReversedEvent;
import com.bankflow.common.exception.ResourceNotFoundException;
import com.bankflow.common.kafka.KafkaTopics;
import com.bankflow.payment.entity.Transaction;
import com.bankflow.payment.repository.OutboxEventRepository;
import com.bankflow.payment.repository.TransactionRepository;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transactional saga state machine for payment-service.
 *
 * <p>Plain English: each Kafka callback delegates here so transaction state changes and outbox row
 * creation happen in one database transaction.
 */
@Service
public class PaymentSagaService {

  private final TransactionRepository transactionRepository;
  private final OutboxEventRepository outboxEventRepository;
  private final OutboxEventFactory outboxEventFactory;

  public PaymentSagaService(
      TransactionRepository transactionRepository,
      OutboxEventRepository outboxEventRepository,
      OutboxEventFactory outboxEventFactory) {
    this.transactionRepository = transactionRepository;
    this.outboxEventRepository = outboxEventRepository;
    this.outboxEventFactory = outboxEventFactory;
  }

  @Transactional
  public boolean recordAccountDebited(AccountDebitedEvent event) {
    Transaction transaction = findTransaction(event.transactionId());
    if (transaction.getSagaStatus() != SagaStatus.STARTED) {
      return false;
    }

    transaction.setStatus(TransactionStatus.PROCESSING);
    transaction.setSagaStatus(SagaStatus.ACCOUNT_DEBITED);
    transactionRepository.save(transaction);

    AccountCreditRequestedEvent creditRequestedEvent = new AccountCreditRequestedEvent(
        UUID.randomUUID(),
        transaction.getId(),
        transaction.getToAccountId(),
        transaction.getAmount(),
        transaction.getCurrency(),
        LocalDateTime.now());
    outboxEventRepository.save(outboxEventFactory.createTransactionOutboxEvent(
        transaction.getId(),
        KafkaTopics.ACCOUNT_CREDIT_REQUESTED,
        creditRequestedEvent));
    return true;
  }

  @Transactional
  public boolean recordAccountCredited(AccountCreditedEvent event) {
    Transaction transaction = findTransaction(event.transactionId());
    if (transaction.getSagaStatus() != SagaStatus.ACCOUNT_DEBITED) {
      return false;
    }

    transaction.setStatus(TransactionStatus.COMPLETED);
    transaction.setSagaStatus(SagaStatus.COMPLETED);
    transaction.setCompletedAt(LocalDateTime.now());
    transaction.setFailureReason(null);
    transactionRepository.save(transaction);

    PaymentCompletedEvent paymentCompletedEvent = new PaymentCompletedEvent(
        UUID.randomUUID(),
        transaction.getId(),
        transaction.getTransactionReference(),
        transaction.getFromAccountId(),
        transaction.getToAccountId(),
        transaction.getAmount(),
        transaction.getCurrency(),
        LocalDateTime.now());
    outboxEventRepository.save(outboxEventFactory.createTransactionOutboxEvent(
        transaction.getId(),
        KafkaTopics.PAYMENT_COMPLETED,
        paymentCompletedEvent));
    return true;
  }

  @Transactional
  public boolean recordDebitFailed(AccountDebitFailedEvent event) {
    Transaction transaction = findTransaction(event.transactionId());
    if (transaction.getSagaStatus() == SagaStatus.FAILED || transaction.getStatus() == TransactionStatus.FAILED) {
      return false;
    }

    transaction.setStatus(TransactionStatus.FAILED);
    transaction.setSagaStatus(SagaStatus.FAILED);
    transaction.setFailureReason(event.reason());
    transaction.setCompletedAt(LocalDateTime.now());
    transactionRepository.save(transaction);

    PaymentFailedEvent paymentFailedEvent = new PaymentFailedEvent(
        UUID.randomUUID(),
        transaction.getId(),
        transaction.getTransactionReference(),
        transaction.getFromAccountId(),
        transaction.getToAccountId(),
        transaction.getAmount(),
        transaction.getCurrency(),
        event.reason(),
        LocalDateTime.now());
    outboxEventRepository.save(outboxEventFactory.createTransactionOutboxEvent(
        transaction.getId(),
        KafkaTopics.PAYMENT_FAILED,
        paymentFailedEvent));
    return true;
  }

  @Transactional
  public boolean recordCreditFailed(AccountCreditFailedEvent event) {
    Transaction transaction = findTransaction(event.transactionId());
    if (transaction.getSagaStatus() != SagaStatus.ACCOUNT_DEBITED) {
      return false;
    }

    transaction.setStatus(TransactionStatus.PROCESSING);
    transaction.setSagaStatus(SagaStatus.COMPENSATING);
    transaction.setFailureReason(event.reason());
    transactionRepository.save(transaction);

    CompensationRequestedEvent compensationRequestedEvent = new CompensationRequestedEvent(
        UUID.randomUUID(),
        transaction.getId(),
        transaction.getFromAccountId(),
        transaction.getAmount(),
        transaction.getCurrency(),
        LocalDateTime.now());
    outboxEventRepository.save(outboxEventFactory.createTransactionOutboxEvent(
        transaction.getId(),
        KafkaTopics.COMPENSATION_REQUESTED,
        compensationRequestedEvent));
    return true;
  }

  @Transactional
  public boolean recordReversalCompleted(AccountReversalCompletedEvent event) {
    Transaction transaction = findTransaction(event.transactionId());
    if (transaction.getSagaStatus() != SagaStatus.COMPENSATING) {
      return false;
    }

    transaction.setStatus(TransactionStatus.REVERSED);
    transaction.setSagaStatus(SagaStatus.COMPENSATED);
    transaction.setCompletedAt(LocalDateTime.now());
    transactionRepository.save(transaction);

    PaymentReversedEvent paymentReversedEvent = new PaymentReversedEvent(
        UUID.randomUUID(),
        transaction.getId(),
        transaction.getTransactionReference(),
        transaction.getFromAccountId(),
        transaction.getToAccountId(),
        transaction.getAmount(),
        transaction.getCurrency(),
        LocalDateTime.now());
    outboxEventRepository.save(outboxEventFactory.createTransactionOutboxEvent(
        transaction.getId(),
        KafkaTopics.PAYMENT_REVERSED,
        paymentReversedEvent));
    return true;
  }

  private Transaction findTransaction(UUID transactionId) {
    return transactionRepository.findById(transactionId)
        .orElseThrow(() -> new ResourceNotFoundException("Transaction", "id", transactionId));
  }
}
