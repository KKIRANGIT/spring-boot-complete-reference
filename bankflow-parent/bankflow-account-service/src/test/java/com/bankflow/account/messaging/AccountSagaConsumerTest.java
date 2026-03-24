package com.bankflow.account.messaging;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bankflow.account.dto.AccountResponse;
import com.bankflow.account.service.AccountCommandService;
import com.bankflow.account.service.AccountEventPublisher;
import com.bankflow.account.service.command.CreditAccountCommand;
import com.bankflow.account.service.command.DebitAccountCommand;
import com.bankflow.common.cache.CacheKeys;
import com.bankflow.common.domain.AccountStatus;
import com.bankflow.common.domain.AccountType;
import com.bankflow.common.event.CompensationRequestedEvent;
import com.bankflow.common.event.PaymentInitiatedEvent;
import com.bankflow.common.exception.AccountFrozenException;
import com.bankflow.common.exception.InsufficientFundsException;
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
 * Unit tests for {@link AccountSagaConsumer}.
 *
 * <p>Plain English: these tests verify that saga messages are processed exactly once, failures are
 * converted into the correct Kafka failure events, and acknowledgments happen only after handling.
 */
@ExtendWith(MockitoExtension.class)
class AccountSagaConsumerTest {

  @Mock
  private AccountCommandService accountCommandService;

  @Mock
  private AccountEventPublisher accountEventPublisher;

  @Mock
  private StringRedisTemplate redisTemplate;

  @Mock
  private ValueOperations<String, String> valueOperations;

  @Mock
  private Acknowledgment acknowledgment;

  @Test
  @DisplayName("Payment initiated should debit once, mark the event processed, and acknowledge after success")
  void handlePaymentInitiated_withSuccess_shouldDebitMarkProcessedAndAck() {
    // Arrange
    AccountSagaConsumer consumer = new AccountSagaConsumer(
        accountCommandService,
        accountEventPublisher,
        redisTemplate);
    PaymentInitiatedEvent event = new PaymentInitiatedEvent(
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        new BigDecimal("250.00"),
        "INR",
        LocalDateTime.now());
    when(redisTemplate.hasKey(CacheKeys.accountProcessed(event.eventId()))).thenReturn(false);
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(accountCommandService.debitAccount(any(DebitAccountCommand.class))).thenReturn(accountResponse(event.fromAccountId()));

    // Act
    consumer.handlePaymentInitiated(event, acknowledgment);

    // Assert
    verify(accountCommandService).debitAccount(any(DebitAccountCommand.class));
    verify(valueOperations).set(eq(CacheKeys.accountProcessed(event.eventId())), eq("processed"), any(Duration.class));
    verify(acknowledgment).acknowledge();
    verify(accountEventPublisher, never()).publishAccountDebitFailed(any());
  }

  @Test
  @DisplayName("Payment initiated should publish ACCOUNT_DEBIT_FAILED when funds are insufficient")
  void handlePaymentInitiated_withInsufficientFunds_shouldPublishFailureAndAck() {
    // Arrange
    AccountSagaConsumer consumer = new AccountSagaConsumer(
        accountCommandService,
        accountEventPublisher,
        redisTemplate);
    PaymentInitiatedEvent event = new PaymentInitiatedEvent(
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        new BigDecimal("750.00"),
        "INR",
        LocalDateTime.now());
    when(redisTemplate.hasKey(CacheKeys.accountProcessed(event.eventId()))).thenReturn(false);
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(accountCommandService.debitAccount(any(DebitAccountCommand.class)))
        .thenThrow(new InsufficientFundsException(event.fromAccountId(), event.amount(), new BigDecimal("100.00")));

    // Act
    consumer.handlePaymentInitiated(event, acknowledgment);

    // Assert
    verify(accountEventPublisher).publishAccountDebitFailed(any());
    verify(valueOperations).set(eq(CacheKeys.accountProcessed(event.eventId())), eq("processed"), any(Duration.class));
    verify(acknowledgment).acknowledge();
  }

  @Test
  @DisplayName("Payment initiated should publish ACCOUNT_DEBIT_FAILED when the account is frozen")
  void handlePaymentInitiated_withFrozenAccount_shouldPublishFailureAndAck() {
    // Arrange
    AccountSagaConsumer consumer = new AccountSagaConsumer(
        accountCommandService,
        accountEventPublisher,
        redisTemplate);
    PaymentInitiatedEvent event = new PaymentInitiatedEvent(
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        new BigDecimal("100.00"),
        "INR",
        LocalDateTime.now());
    when(redisTemplate.hasKey(CacheKeys.accountProcessed(event.eventId()))).thenReturn(false);
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(accountCommandService.debitAccount(any(DebitAccountCommand.class)))
        .thenThrow(new AccountFrozenException(event.fromAccountId()));

    // Act
    consumer.handlePaymentInitiated(event, acknowledgment);

    // Assert
    verify(accountEventPublisher).publishAccountDebitFailed(any());
    verify(acknowledgment).acknowledge();
  }

  @Test
  @DisplayName("Already-processed payment events should be acknowledged without running the debit again")
  void handlePaymentInitiated_withDuplicateEvent_shouldAckWithoutProcessing() {
    // Arrange
    AccountSagaConsumer consumer = new AccountSagaConsumer(
        accountCommandService,
        accountEventPublisher,
        redisTemplate);
    PaymentInitiatedEvent event = new PaymentInitiatedEvent(
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        new BigDecimal("100.00"),
        "INR",
        LocalDateTime.now());
    when(redisTemplate.hasKey(CacheKeys.accountProcessed(event.eventId()))).thenReturn(true);

    // Act
    consumer.handlePaymentInitiated(event, acknowledgment);

    // Assert
    verify(accountCommandService, never()).debitAccount(any(DebitAccountCommand.class));
    verify(acknowledgment).acknowledge();
  }

  @Test
  @DisplayName("Compensation requested should credit the account back, publish reversal completion, and acknowledge")
  void handleCompensationRequested_shouldCreditPublishReversalAndAck() {
    // Arrange
    AccountSagaConsumer consumer = new AccountSagaConsumer(
        accountCommandService,
        accountEventPublisher,
        redisTemplate);
    CompensationRequestedEvent event = new CompensationRequestedEvent(
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        new BigDecimal("150.00"),
        "INR",
        LocalDateTime.now());
    when(redisTemplate.hasKey(CacheKeys.accountProcessed(event.eventId()))).thenReturn(false);
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(accountCommandService.creditAccount(any(CreditAccountCommand.class))).thenReturn(accountResponse(event.accountId()));

    // Act
    consumer.handleCompensationRequested(event, acknowledgment);

    // Assert
    verify(accountCommandService).creditAccount(any(CreditAccountCommand.class));
    verify(accountEventPublisher).publishAccountReversalCompleted(any());
    verify(valueOperations).set(eq(CacheKeys.accountProcessed(event.eventId())), eq("processed"), any(Duration.class));
    verify(acknowledgment).acknowledge();
  }

  private AccountResponse accountResponse(UUID accountId) {
    return new AccountResponse(
        accountId,
        "BNK123456789",
        UUID.randomUUID(),
        AccountType.SAVINGS,
        new BigDecimal("850.00"),
        "INR",
        AccountStatus.ACTIVE,
        LocalDateTime.now().minusDays(1),
        LocalDateTime.now());
  }
}
