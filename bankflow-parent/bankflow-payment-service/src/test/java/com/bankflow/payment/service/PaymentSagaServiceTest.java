package com.bankflow.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bankflow.common.domain.SagaStatus;
import com.bankflow.common.domain.TransactionStatus;
import com.bankflow.common.event.AccountCreditedEvent;
import com.bankflow.common.event.AccountCreditFailedEvent;
import com.bankflow.common.event.AccountDebitFailedEvent;
import com.bankflow.common.event.AccountDebitedEvent;
import com.bankflow.common.event.AccountReversalCompletedEvent;
import com.bankflow.common.exception.ResourceNotFoundException;
import com.bankflow.common.kafka.KafkaTopics;
import com.bankflow.payment.entity.OutboxEvent;
import com.bankflow.payment.entity.Transaction;
import com.bankflow.payment.repository.OutboxEventRepository;
import com.bankflow.payment.repository.TransactionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link PaymentSagaService}.
 *
 * <p>Plain English: these tests prove each saga callback updates payment state and writes the next
 * durable outbox event in the same step.
 */
@ExtendWith(MockitoExtension.class)
class PaymentSagaServiceTest {

  @Mock
  private TransactionRepository transactionRepository;

  @Mock
  private OutboxEventRepository outboxEventRepository;

  @Captor
  private ArgumentCaptor<Transaction> transactionCaptor;

  @Captor
  private ArgumentCaptor<OutboxEvent> outboxEventCaptor;

  @Test
  @DisplayName("Handling ACCOUNT_DEBITED should move the saga to ACCOUNT_DEBITED and create the credit-request outbox event")
  void handleAccountDebited_shouldUpdateSagaStatusAndCreateCreditRequestedEvent() {
    // Arrange
    PaymentSagaService paymentSagaService = newService();
    Transaction transaction = startedTransaction();
    when(transactionRepository.findById(transaction.getId())).thenReturn(Optional.of(transaction));

    AccountDebitedEvent event = new AccountDebitedEvent(
        UUID.randomUUID(),
        transaction.getId(),
        transaction.getFromAccountId(),
        transaction.getAmount(),
        new BigDecimal("700.00"),
        LocalDateTime.now());

    // Act
    boolean handled = paymentSagaService.recordAccountDebited(event);

    // Assert
    // This catches the saga bug where the source account is debited but payment-service forgets to
    // request the destination credit, leaving the transfer half-finished forever.
    verify(transactionRepository).save(transactionCaptor.capture());
    verify(outboxEventRepository).save(outboxEventCaptor.capture());

    assertThat(handled).isTrue();
    assertThat(transactionCaptor.getValue().getSagaStatus()).isEqualTo(SagaStatus.ACCOUNT_DEBITED);
    assertThat(transactionCaptor.getValue().getStatus()).isEqualTo(TransactionStatus.PROCESSING);
    assertThat(outboxEventCaptor.getValue().getEventType()).isEqualTo(KafkaTopics.ACCOUNT_CREDIT_REQUESTED);
  }

  @Test
  @DisplayName("Handling ACCOUNT_CREDITED should mark the transaction completed and emit PAYMENT_COMPLETED")
  void handleAccountCredited_shouldMarkTransactionCompleted() {
    // Arrange
    PaymentSagaService paymentSagaService = newService();
    Transaction transaction = startedTransaction();
    transaction.setSagaStatus(SagaStatus.ACCOUNT_DEBITED);
    when(transactionRepository.findById(transaction.getId())).thenReturn(Optional.of(transaction));

    AccountCreditedEvent event = new AccountCreditedEvent(
        UUID.randomUUID(),
        transaction.getId(),
        transaction.getToAccountId(),
        transaction.getAmount(),
        new BigDecimal("800.00"),
        LocalDateTime.now());

    // Act
    boolean handled = paymentSagaService.recordAccountCredited(event);

    // Assert
    verify(transactionRepository).save(transactionCaptor.capture());
    verify(outboxEventRepository).save(outboxEventCaptor.capture());

    Transaction savedTransaction = transactionCaptor.getValue();
    assertThat(handled).isTrue();
    assertThat(savedTransaction.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
    assertThat(savedTransaction.getSagaStatus()).isEqualTo(SagaStatus.COMPLETED);
    assertThat(savedTransaction.getCompletedAt()).isNotNull();
    assertThat(outboxEventCaptor.getValue().getEventType()).isEqualTo(KafkaTopics.PAYMENT_COMPLETED);
  }

  @Test
  @DisplayName("Handling ACCOUNT_DEBIT_FAILED should fail the transaction and publish PAYMENT_FAILED")
  void handleDebitFailed_shouldMarkTransactionFailed() {
    // Arrange
    PaymentSagaService paymentSagaService = newService();
    Transaction transaction = startedTransaction();
    when(transactionRepository.findById(transaction.getId())).thenReturn(Optional.of(transaction));

    AccountDebitFailedEvent event = new AccountDebitFailedEvent(
        UUID.randomUUID(),
        transaction.getId(),
        transaction.getFromAccountId(),
        transaction.getAmount(),
        "INSUFFICIENT_FUNDS",
        LocalDateTime.now());

    // Act
    boolean handled = paymentSagaService.recordDebitFailed(event);

    // Assert
    verify(transactionRepository).save(transactionCaptor.capture());
    verify(outboxEventRepository).save(outboxEventCaptor.capture());

    assertThat(handled).isTrue();
    assertThat(transactionCaptor.getValue().getStatus()).isEqualTo(TransactionStatus.FAILED);
    assertThat(transactionCaptor.getValue().getSagaStatus()).isEqualTo(SagaStatus.FAILED);
    assertThat(transactionCaptor.getValue().getFailureReason()).isEqualTo("INSUFFICIENT_FUNDS");
    assertThat(outboxEventCaptor.getValue().getEventType()).isEqualTo(KafkaTopics.PAYMENT_FAILED);
  }

  @Test
  @DisplayName("Handling ACCOUNT_CREDIT_FAILED should start compensation and persist a compensation-request outbox event")
  void handleCreditFailed_shouldSetSagaToCompensatingAndSaveCompensationEvent() {
    // Arrange
    PaymentSagaService paymentSagaService = newService();
    Transaction transaction = startedTransaction();
    transaction.setSagaStatus(SagaStatus.ACCOUNT_DEBITED);
    when(transactionRepository.findById(transaction.getId())).thenReturn(Optional.of(transaction));

    AccountCreditFailedEvent event = new AccountCreditFailedEvent(
        UUID.randomUUID(),
        transaction.getId(),
        transaction.getToAccountId(),
        transaction.getAmount(),
        "ACCOUNT_CLOSED",
        LocalDateTime.now());

    // Act
    boolean handled = paymentSagaService.recordCreditFailed(event);

    // Assert
    verify(transactionRepository).save(transactionCaptor.capture());
    verify(outboxEventRepository).save(outboxEventCaptor.capture());

    assertThat(handled).isTrue();
    assertThat(transactionCaptor.getValue().getSagaStatus()).isEqualTo(SagaStatus.COMPENSATING);
    assertThat(transactionCaptor.getValue().getFailureReason()).isEqualTo("ACCOUNT_CLOSED");
    assertThat(outboxEventCaptor.getValue().getEventType()).isEqualTo(KafkaTopics.COMPENSATION_REQUESTED);
  }

  @Test
  @DisplayName("Handling ACCOUNT_REVERSAL_COMPLETED should mark the transaction reversed and compensated")
  void handleReversalCompleted_shouldSetTransactionReversedAndCompensated() {
    // Arrange
    PaymentSagaService paymentSagaService = newService();
    Transaction transaction = startedTransaction();
    transaction.setSagaStatus(SagaStatus.COMPENSATING);
    when(transactionRepository.findById(transaction.getId())).thenReturn(Optional.of(transaction));

    AccountReversalCompletedEvent event = new AccountReversalCompletedEvent(
        UUID.randomUUID(),
        transaction.getId(),
        transaction.getFromAccountId(),
        transaction.getAmount(),
        new BigDecimal("1000.00"),
        LocalDateTime.now());

    // Act
    boolean handled = paymentSagaService.recordReversalCompleted(event);

    // Assert
    verify(transactionRepository).save(transactionCaptor.capture());
    verify(outboxEventRepository).save(outboxEventCaptor.capture());

    Transaction savedTransaction = transactionCaptor.getValue();
    assertThat(handled).isTrue();
    assertThat(savedTransaction.getStatus()).isEqualTo(TransactionStatus.REVERSED);
    assertThat(savedTransaction.getSagaStatus()).isEqualTo(SagaStatus.COMPENSATED);
    assertThat(savedTransaction.getCompletedAt()).isNotNull();
    assertThat(outboxEventCaptor.getValue().getEventType()).isEqualTo(KafkaTopics.PAYMENT_REVERSED);
  }

  @Test
  @DisplayName("Looking up a missing transaction during saga handling should raise a not-found exception")
  void missingTransaction_shouldThrowResourceNotFoundException() {
    // Arrange
    PaymentSagaService paymentSagaService = newService();
    UUID transactionId = UUID.randomUUID();
    when(transactionRepository.findById(transactionId)).thenReturn(Optional.empty());

    AccountDebitedEvent event = new AccountDebitedEvent(
        UUID.randomUUID(),
        transactionId,
        UUID.randomUUID(),
        new BigDecimal("10.00"),
        new BigDecimal("90.00"),
        LocalDateTime.now());

    // Act + Assert
    assertThatThrownBy(() -> paymentSagaService.recordAccountDebited(event))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  private PaymentSagaService newService() {
    return new PaymentSagaService(
        transactionRepository,
        outboxEventRepository,
        new OutboxEventFactory(new ObjectMapper().registerModule(new JavaTimeModule())));
  }

  private Transaction startedTransaction() {
    Transaction transaction = new Transaction();
    transaction.setId(UUID.randomUUID());
    transaction.setTransactionReference("TXN17100000000001234");
    transaction.setIdempotencyKey("idem-saga");
    transaction.setFromAccountId(UUID.randomUUID());
    transaction.setToAccountId(UUID.randomUUID());
    transaction.setAmount(new BigDecimal("300.00"));
    transaction.setCurrency("INR");
    transaction.setStatus(TransactionStatus.PENDING);
    transaction.setSagaStatus(SagaStatus.STARTED);
    transaction.setCreatedAt(LocalDateTime.now().minusMinutes(1));
    return transaction;
  }
}
