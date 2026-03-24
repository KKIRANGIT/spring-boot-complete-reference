package com.bankflow.payment.messaging;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bankflow.common.event.AccountCreditedEvent;
import com.bankflow.common.event.AccountCreditFailedEvent;
import com.bankflow.common.event.AccountDebitFailedEvent;
import com.bankflow.common.event.AccountDebitedEvent;
import com.bankflow.common.event.AccountReversalCompletedEvent;
import com.bankflow.payment.service.PaymentSagaService;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.kafka.support.Acknowledgment;

/**
 * Unit tests for {@link PaymentSagaConsumer}.
 *
 * <p>Plain English: these tests verify Kafka redelivery idempotency and the mapping from each
 * incoming account-service event to the correct saga service method.
 */
@ExtendWith(MockitoExtension.class)
class PaymentSagaConsumerTest {

  @Mock
  private PaymentSagaService paymentSagaService;

  @Mock
  private StringRedisTemplate redisTemplate;

  @Mock
  private ValueOperations<String, String> valueOperations;

  @Mock
  private Acknowledgment acknowledgment;

  @Test
  @DisplayName("A duplicate ACCOUNT_DEBITED event should be acknowledged without reprocessing the saga")
  void handleAccountDebited_whenAlreadyProcessed_shouldOnlyAcknowledge() {
    // Arrange
    PaymentSagaConsumer consumer = new PaymentSagaConsumer(paymentSagaService, redisTemplate);
    AccountDebitedEvent event = debitedEvent();
    when(redisTemplate.hasKey("saga:debited:" + event.transactionId())).thenReturn(true);

    // Act
    consumer.handleAccountDebited(event, acknowledgment);

    // Assert
    // This prevents the double-processing bug where Kafka redelivery would trigger a second credit
    // request for the same payment after the first saga step already succeeded.
    verify(paymentSagaService, never()).recordAccountDebited(event);
    verify(acknowledgment).acknowledge();
  }

  @Test
  @DisplayName("A fresh ACCOUNT_DEBITED event should update the saga and mark the Redis deduplication key")
  void handleAccountDebited_whenNewEvent_shouldProcessAndMarkRedis() {
    // Arrange
    PaymentSagaConsumer consumer = new PaymentSagaConsumer(paymentSagaService, redisTemplate);
    AccountDebitedEvent event = debitedEvent();
    when(redisTemplate.hasKey("saga:debited:" + event.transactionId())).thenReturn(false);
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);

    // Act
    consumer.handleAccountDebited(event, acknowledgment);

    // Assert
    // This catches the reliability bug where the event is processed but the idempotency marker is
    // not written, causing the same Kafka record to be replayed as a second business action.
    verify(paymentSagaService).recordAccountDebited(event);
    verify(valueOperations).set("saga:debited:" + event.transactionId(), "processed", Duration.ofDays(1));
    verify(acknowledgment).acknowledge();
  }

  @Test
  @DisplayName("ACCOUNT_CREDITED should delegate to the credit-complete saga transition")
  void handleAccountCredited_shouldDelegateAndAcknowledge() {
    // Arrange
    PaymentSagaConsumer consumer = new PaymentSagaConsumer(paymentSagaService, redisTemplate);
    AccountCreditedEvent event = new AccountCreditedEvent(
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        new BigDecimal("100.00"),
        new BigDecimal("900.00"),
        LocalDateTime.now());
    when(redisTemplate.hasKey("saga:credited:" + event.transactionId())).thenReturn(false);
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);

    // Act
    consumer.handleAccountCredited(event, acknowledgment);

    // Assert
    verify(paymentSagaService).recordAccountCredited(event);
    verify(acknowledgment).acknowledge();
  }

  @Test
  @DisplayName("ACCOUNT_DEBIT_FAILED should delegate to the debit-failed saga transition")
  void handleDebitFailed_shouldDelegateAndAcknowledge() {
    // Arrange
    PaymentSagaConsumer consumer = new PaymentSagaConsumer(paymentSagaService, redisTemplate);
    AccountDebitFailedEvent event = new AccountDebitFailedEvent(
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        new BigDecimal("100.00"),
        "INSUFFICIENT_FUNDS",
        LocalDateTime.now());
    when(redisTemplate.hasKey("saga:debit-failed:" + event.transactionId())).thenReturn(false);
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);

    // Act
    consumer.handleDebitFailed(event, acknowledgment);

    // Assert
    verify(paymentSagaService).recordDebitFailed(event);
    verify(acknowledgment).acknowledge();
  }

  @Test
  @DisplayName("ACCOUNT_CREDIT_FAILED should delegate to the compensation saga transition")
  void handleCreditFailed_shouldDelegateAndAcknowledge() {
    // Arrange
    PaymentSagaConsumer consumer = new PaymentSagaConsumer(paymentSagaService, redisTemplate);
    AccountCreditFailedEvent event = new AccountCreditFailedEvent(
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        new BigDecimal("100.00"),
        "ACCOUNT_CLOSED",
        LocalDateTime.now());
    when(redisTemplate.hasKey("saga:credit-failed:" + event.transactionId())).thenReturn(false);
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);

    // Act
    consumer.handleCreditFailed(event, acknowledgment);

    // Assert
    verify(paymentSagaService).recordCreditFailed(event);
    verify(acknowledgment).acknowledge();
  }

  @Test
  @DisplayName("ACCOUNT_REVERSAL_COMPLETED should delegate to the compensation-complete saga transition")
  void handleReversalCompleted_shouldDelegateAndAcknowledge() {
    // Arrange
    PaymentSagaConsumer consumer = new PaymentSagaConsumer(paymentSagaService, redisTemplate);
    AccountReversalCompletedEvent event = new AccountReversalCompletedEvent(
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        new BigDecimal("100.00"),
        new BigDecimal("1000.00"),
        LocalDateTime.now());
    when(redisTemplate.hasKey("saga:reversal-completed:" + event.transactionId())).thenReturn(false);
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);

    // Act
    consumer.handleReversalCompleted(event, acknowledgment);

    // Assert
    verify(paymentSagaService).recordReversalCompleted(event);
    verify(acknowledgment).acknowledge();
  }

  private AccountDebitedEvent debitedEvent() {
    return new AccountDebitedEvent(
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        new BigDecimal("100.00"),
        new BigDecimal("900.00"),
        LocalDateTime.now());
  }
}
