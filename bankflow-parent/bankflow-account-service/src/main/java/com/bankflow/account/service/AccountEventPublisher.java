package com.bankflow.account.service;

import com.bankflow.common.event.AccountCreatedEvent;
import com.bankflow.common.event.AccountCreditedEvent;
import com.bankflow.common.event.AccountDebitFailedEvent;
import com.bankflow.common.event.AccountDebitedEvent;
import com.bankflow.common.event.AccountReversalCompletedEvent;
import com.bankflow.common.kafka.KafkaTopics;
import java.util.UUID;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Central producer for account-service Kafka events.
 *
 * <p>Plain English: this isolates topic names and message publishing from business logic.
 *
 * <p>Bug prevented: scattering topic writes across services makes it easy to publish the right
 * event shape to the wrong topic during a refactor.
 */
@Component
public class AccountEventPublisher {

  private final KafkaTemplate<String, Object> kafkaTemplate;

  public AccountEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
    this.kafkaTemplate = kafkaTemplate;
  }

  /**
   * Publishes a new-account event.
   */
  public void publishAccountCreated(AccountCreatedEvent event) {
    kafkaTemplate.send(KafkaTopics.ACCOUNT_CREATED, event.accountId().toString(), event);
  }

  /**
   * Publishes a successful debit event.
   */
  public void publishAccountDebited(AccountDebitedEvent event) {
    kafkaTemplate.send(KafkaTopics.ACCOUNT_DEBITED, event.accountId().toString(), event);
  }

  /**
   * Publishes a successful credit event.
   */
  public void publishAccountCredited(AccountCreditedEvent event) {
    kafkaTemplate.send(KafkaTopics.ACCOUNT_CREDITED, event.accountId().toString(), event);
  }

  /**
   * Publishes a failed-debit event with a machine-readable reason.
   */
  public void publishAccountDebitFailed(AccountDebitFailedEvent event) {
    kafkaTemplate.send(KafkaTopics.ACCOUNT_DEBIT_FAILED, keyFrom(event.transactionId()), event);
  }

  /**
   * Publishes a completed reversal event for saga compensation.
   */
  public void publishAccountReversalCompleted(AccountReversalCompletedEvent event) {
    kafkaTemplate.send(KafkaTopics.ACCOUNT_REVERSAL_COMPLETED, keyFrom(event.transactionId()), event);
  }

  private String keyFrom(UUID transactionId) {
    return transactionId == null ? UUID.randomUUID().toString() : transactionId.toString();
  }
}
