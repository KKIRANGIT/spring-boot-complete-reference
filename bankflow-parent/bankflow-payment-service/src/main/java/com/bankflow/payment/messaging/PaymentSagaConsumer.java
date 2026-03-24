package com.bankflow.payment.messaging;

import com.bankflow.common.event.AccountCreditedEvent;
import com.bankflow.common.event.AccountCreditFailedEvent;
import com.bankflow.common.event.AccountDebitFailedEvent;
import com.bankflow.common.event.AccountDebitedEvent;
import com.bankflow.common.event.AccountReversalCompletedEvent;
import com.bankflow.common.kafka.KafkaTopics;
import com.bankflow.payment.service.PaymentSagaService;
import java.time.Duration;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Kafka listeners that advance the payment saga based on account-service outcomes.
 *
 * <p>Plain English: this class is the choreography brain of payment-service. It reacts to debit,
 * credit, failure, and compensation events from account-service.
 */
@Component
public class PaymentSagaConsumer {

  private static final Duration PROCESSED_EVENT_TTL = Duration.ofDays(1);

  private final PaymentSagaService paymentSagaService;
  private final StringRedisTemplate redisTemplate;

  public PaymentSagaConsumer(
      PaymentSagaService paymentSagaService,
      StringRedisTemplate redisTemplate) {
    this.paymentSagaService = paymentSagaService;
    this.redisTemplate = redisTemplate;
  }

  @KafkaListener(topics = KafkaTopics.ACCOUNT_DEBITED, groupId = "payment-service-saga-group")
  public void handleAccountDebited(AccountDebitedEvent event, Acknowledgment acknowledgment) {
    String processedKey = processedKey("saga:debited:", event.transactionId());
    if (isAlreadyProcessed(processedKey)) {
      acknowledgment.acknowledge();
      return;
    }

    paymentSagaService.recordAccountDebited(event);
    markProcessed(processedKey);
    acknowledgment.acknowledge();
  }

  @KafkaListener(topics = KafkaTopics.ACCOUNT_CREDITED, groupId = "payment-service-saga-group")
  public void handleAccountCredited(AccountCreditedEvent event, Acknowledgment acknowledgment) {
    String processedKey = processedKey("saga:credited:", event.transactionId());
    if (isAlreadyProcessed(processedKey)) {
      acknowledgment.acknowledge();
      return;
    }

    paymentSagaService.recordAccountCredited(event);
    markProcessed(processedKey);
    acknowledgment.acknowledge();
  }

  @KafkaListener(topics = KafkaTopics.ACCOUNT_DEBIT_FAILED, groupId = "payment-service-saga-group")
  public void handleDebitFailed(AccountDebitFailedEvent event, Acknowledgment acknowledgment) {
    String processedKey = processedKey("saga:debit-failed:", event.transactionId());
    if (isAlreadyProcessed(processedKey)) {
      acknowledgment.acknowledge();
      return;
    }

    paymentSagaService.recordDebitFailed(event);
    markProcessed(processedKey);
    acknowledgment.acknowledge();
  }

  @KafkaListener(topics = KafkaTopics.ACCOUNT_CREDIT_FAILED, groupId = "payment-service-saga-group")
  public void handleCreditFailed(AccountCreditFailedEvent event, Acknowledgment acknowledgment) {
    String processedKey = processedKey("saga:credit-failed:", event.transactionId());
    if (isAlreadyProcessed(processedKey)) {
      acknowledgment.acknowledge();
      return;
    }

    paymentSagaService.recordCreditFailed(event);
    markProcessed(processedKey);
    acknowledgment.acknowledge();
  }

  @KafkaListener(topics = KafkaTopics.ACCOUNT_REVERSAL_COMPLETED, groupId = "payment-service-saga-group")
  public void handleReversalCompleted(AccountReversalCompletedEvent event, Acknowledgment acknowledgment) {
    String processedKey = processedKey("saga:reversal-completed:", event.transactionId());
    if (isAlreadyProcessed(processedKey)) {
      acknowledgment.acknowledge();
      return;
    }

    paymentSagaService.recordReversalCompleted(event);
    markProcessed(processedKey);
    acknowledgment.acknowledge();
  }

  private boolean isAlreadyProcessed(String key) {
    return Boolean.TRUE.equals(redisTemplate.hasKey(key));
  }

  private void markProcessed(String key) {
    redisTemplate.opsForValue().set(key, "processed", PROCESSED_EVENT_TTL);
  }

  private String processedKey(String prefix, UUID transactionId) {
    return prefix + transactionId;
  }
}
