package com.bankflow.account.messaging;

import com.bankflow.account.dto.AccountResponse;
import com.bankflow.account.service.AccountCommandService;
import com.bankflow.account.service.AccountEventPublisher;
import com.bankflow.account.service.command.CreditAccountCommand;
import com.bankflow.account.service.command.DebitAccountCommand;
import com.bankflow.common.cache.CacheKeys;
import com.bankflow.common.event.AccountCreditFailedEvent;
import com.bankflow.common.event.AccountCreditRequestedEvent;
import com.bankflow.common.event.AccountDebitFailedEvent;
import com.bankflow.common.event.AccountReversalCompletedEvent;
import com.bankflow.common.event.CompensationRequestedEvent;
import com.bankflow.common.event.PaymentInitiatedEvent;
import com.bankflow.common.exception.AccountFrozenException;
import com.bankflow.common.exception.InsufficientFundsException;
import com.bankflow.common.exception.ResourceNotFoundException;
import com.bankflow.common.kafka.KafkaTopics;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Kafka saga consumers for account-side payment orchestration.
 *
 * <p>Plain English: this class listens for payment events, executes the local balance mutation, and
 * emits the next event in the saga.
 *
 * <p>Design decision: acknowledgments happen only after processing and idempotency bookkeeping so a
 * crash before that point causes safe redelivery instead of silent message loss.
 *
 * <p>Interview question answered: "How do you consume Kafka saga events safely without double
 * debiting an account?"
 */
@Component
public class AccountSagaConsumer {

  private static final Logger log = LoggerFactory.getLogger(AccountSagaConsumer.class);
  private static final Duration PROCESSED_EVENT_TTL = Duration.ofDays(1);

  private final AccountCommandService accountCommandService;
  private final AccountEventPublisher accountEventPublisher;
  private final StringRedisTemplate redisTemplate;

  public AccountSagaConsumer(
      AccountCommandService accountCommandService,
      AccountEventPublisher accountEventPublisher,
      StringRedisTemplate redisTemplate) {
    this.accountCommandService = accountCommandService;
    this.accountEventPublisher = accountEventPublisher;
    this.redisTemplate = redisTemplate;
  }

  @KafkaListener(topics = KafkaTopics.PAYMENT_INITIATED)
  public void handlePaymentInitiated(PaymentInitiatedEvent event, Acknowledgment acknowledgment) {
    String processedKey = CacheKeys.accountProcessed(event.eventId());
    if (Boolean.TRUE.equals(redisTemplate.hasKey(processedKey))) {
      acknowledgment.acknowledge();
      return;
    }

    try {
      accountCommandService.debitAccount(new DebitAccountCommand(
          event.fromAccountId(),
          event.amount(),
          event.transactionId(),
          null,
          "Payment debit for transaction " + event.transactionId()));

      markProcessed(processedKey);
      acknowledgment.acknowledge();
    } catch (InsufficientFundsException ex) {
      publishDebitFailure(event, "INSUFFICIENT_FUNDS");
      markProcessed(processedKey);
      acknowledgment.acknowledge();
    } catch (AccountFrozenException ex) {
      publishDebitFailure(event, "ACCOUNT_FROZEN");
      markProcessed(processedKey);
      acknowledgment.acknowledge();
    } catch (RuntimeException ex) {
      log.error("Unexpected failure while handling payment event {}", event.eventId(), ex);
      throw ex;
    }
  }

  /**
   * Credits the destination account after the source debit succeeds.
   */
  @KafkaListener(topics = KafkaTopics.ACCOUNT_CREDIT_REQUESTED)
  public void handleAccountCreditRequested(
      AccountCreditRequestedEvent event,
      Acknowledgment acknowledgment) {
    String processedKey = CacheKeys.accountProcessed(event.eventId());
    if (Boolean.TRUE.equals(redisTemplate.hasKey(processedKey))) {
      acknowledgment.acknowledge();
      return;
    }

    try {
      accountCommandService.creditAccount(new CreditAccountCommand(
          event.toAccountId(),
          event.amount(),
          event.transactionId(),
          null,
          "Payment credit for transaction " + event.transactionId()));
      markProcessed(processedKey);
      acknowledgment.acknowledge();
    } catch (ResourceNotFoundException ex) {
      publishCreditFailure(event, "RESOURCE_NOT_FOUND");
      markProcessed(processedKey);
      acknowledgment.acknowledge();
    } catch (IllegalStateException ex) {
      publishCreditFailure(event, "ACCOUNT_CLOSED");
      markProcessed(processedKey);
      acknowledgment.acknowledge();
    } catch (RuntimeException ex) {
      log.error("Unexpected failure while crediting destination account for event {}", event.eventId(), ex);
      throw ex;
    }
  }

  @KafkaListener(topics = KafkaTopics.COMPENSATION_REQUESTED)
  public void handleCompensationRequested(
      CompensationRequestedEvent event,
      Acknowledgment acknowledgment) {
    String processedKey = CacheKeys.accountProcessed(event.eventId());
    if (Boolean.TRUE.equals(redisTemplate.hasKey(processedKey))) {
      acknowledgment.acknowledge();
      return;
    }

    AccountResponse creditedAccount = accountCommandService.creditAccount(new CreditAccountCommand(
        event.accountId(),
        event.amount(),
        event.transactionId(),
        null,
        "Compensation credit for transaction " + event.transactionId()));

    accountEventPublisher.publishAccountReversalCompleted(new AccountReversalCompletedEvent(
        UUID.randomUUID(),
        event.transactionId(),
        event.accountId(),
        event.amount(),
        creditedAccount.balance(),
        LocalDateTime.now()));
    markProcessed(processedKey);
    acknowledgment.acknowledge();
  }

  private void publishDebitFailure(PaymentInitiatedEvent event, String reason) {
    accountEventPublisher.publishAccountDebitFailed(new AccountDebitFailedEvent(
        UUID.randomUUID(),
        event.transactionId(),
        event.fromAccountId(),
        event.amount(),
        reason,
        LocalDateTime.now()));
  }

  private void publishCreditFailure(AccountCreditRequestedEvent event, String reason) {
    accountEventPublisher.publishAccountCreditFailed(new AccountCreditFailedEvent(
        UUID.randomUUID(),
        event.transactionId(),
        event.toAccountId(),
        event.amount(),
        reason,
        LocalDateTime.now()));
  }

  private void markProcessed(String key) {
    redisTemplate.opsForValue().set(key, "processed", PROCESSED_EVENT_TTL);
  }
}
