package com.bankflow.account.service;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.bankflow.common.event.AccountDebitFailedEvent;
import com.bankflow.common.event.AccountReversalCompletedEvent;
import com.bankflow.common.kafka.KafkaTopics;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * Unit tests for {@link AccountEventPublisher}.
 *
 * <p>Plain English: this proves the publisher routes failure and compensation events to the correct
 * Kafka topics so downstream saga steps do not miss them.
 */
class AccountEventPublisherTest {

  @Test
  @DisplayName("Debit failure events should go to ACCOUNT_DEBIT_FAILED with the transaction id as the key")
  void publishAccountDebitFailed_shouldSendFailureTopic() {
    // Arrange
    KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);
    AccountEventPublisher publisher = new AccountEventPublisher(kafkaTemplate);
    UUID transactionId = UUID.randomUUID();
    AccountDebitFailedEvent event = new AccountDebitFailedEvent(
        UUID.randomUUID(),
        transactionId,
        UUID.randomUUID(),
        new BigDecimal("300.00"),
        "INSUFFICIENT_FUNDS",
        LocalDateTime.now());

    // Act
    publisher.publishAccountDebitFailed(event);

    // Assert
    verify(kafkaTemplate).send(
        eq(KafkaTopics.ACCOUNT_DEBIT_FAILED),
        eq(transactionId.toString()),
        eq(event));
  }

  @Test
  @DisplayName("Reversal completed events should go to ACCOUNT_REVERSAL_COMPLETED with the transaction id as the key")
  void publishAccountReversalCompleted_shouldSendReversalTopic() {
    // Arrange
    KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);
    AccountEventPublisher publisher = new AccountEventPublisher(kafkaTemplate);
    UUID transactionId = UUID.randomUUID();
    AccountReversalCompletedEvent event = new AccountReversalCompletedEvent(
        UUID.randomUUID(),
        transactionId,
        UUID.randomUUID(),
        new BigDecimal("300.00"),
        new BigDecimal("1000.00"),
        LocalDateTime.now());

    // Act
    publisher.publishAccountReversalCompleted(event);

    // Assert
    verify(kafkaTemplate).send(
        eq(KafkaTopics.ACCOUNT_REVERSAL_COMPLETED),
        eq(transactionId.toString()),
        eq(event));
  }
}
