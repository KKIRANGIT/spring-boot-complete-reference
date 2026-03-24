package com.bankflow.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bankflow.common.cache.CacheKeys;
import com.bankflow.common.exception.ValidationException;
import com.bankflow.common.kafka.KafkaTopics;
import com.bankflow.payment.dto.TransferRequest;
import com.bankflow.payment.dto.TransferResponse;
import com.bankflow.payment.entity.OutboxEvent;
import com.bankflow.payment.entity.Transaction;
import com.bankflow.payment.repository.OutboxEventRepository;
import com.bankflow.payment.repository.TransactionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.math.BigDecimal;
import java.time.Duration;
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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

/**
 * Unit tests for {@link PaymentService}.
 *
 * <p>Plain English: these tests verify idempotency, validation, and the outbox-backed transfer
 * creation flow without booting Spring.
 */
@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

  @Mock
  private TransactionRepository transactionRepository;

  @Mock
  private OutboxEventRepository outboxEventRepository;

  @Mock
  private TransactionReferenceGenerator transactionReferenceGenerator;

  @Mock
  private StringRedisTemplate redisTemplate;

  @Mock
  private ValueOperations<String, String> valueOperations;

  @Captor
  private ArgumentCaptor<OutboxEvent> outboxEventCaptor;

  @Captor
  private ArgumentCaptor<Transaction> transactionCaptor;

  private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

  @Test
  @DisplayName("A new transfer request should create the transaction, matching outbox row, and idempotency cache entry")
  void initiateTransfer_newRequest_shouldCreateTransactionAndOutboxEvent() {
    // Arrange
    PaymentTransactionWriter writer = new PaymentTransactionWriter(
        transactionRepository,
        outboxEventRepository,
        transactionReferenceGenerator,
        new OutboxEventFactory(objectMapper));
    PaymentService paymentService = new PaymentService(
        transactionRepository,
        writer,
        new PaymentMapper(),
        redisTemplate,
        objectMapper,
        24);

    TransferRequest request = new TransferRequest(
        UUID.randomUUID(),
        UUID.randomUUID(),
        new BigDecimal("250.00"),
        "inr",
        "Rent payment");
    String idempotencyKey = "idem-new-request";

    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.get(CacheKeys.IDEMPOTENCY_PREFIX + idempotencyKey)).thenReturn(null);
    when(transactionRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
    when(transactionReferenceGenerator.generate()).thenReturn("TXN17100000000001234");
    when(transactionRepository.saveAndFlush(any(Transaction.class))).thenAnswer(invocation -> {
      Transaction transaction = invocation.getArgument(0);
      transaction.setCreatedAt(LocalDateTime.now());
      return transaction;
    });

    // Act
    TransferResponse response = paymentService.initiateTransfer(request, idempotencyKey);

    // Assert
    // This catches the production bug where a payment row is stored but the outbox row is forgotten,
    // leaving the saga stuck forever with no Kafka event to continue it.
    verify(transactionRepository).saveAndFlush(transactionCaptor.capture());
    verify(outboxEventRepository).save(outboxEventCaptor.capture());
    verify(valueOperations).set(
        eq(CacheKeys.IDEMPOTENCY_PREFIX + idempotencyKey),
        any(String.class),
        eq(Duration.ofHours(24)));

    Transaction savedTransaction = transactionCaptor.getValue();
    OutboxEvent savedOutboxEvent = outboxEventCaptor.getValue();

    assertThat(savedTransaction.getIdempotencyKey()).isEqualTo(idempotencyKey);
    assertThat(savedTransaction.getCurrency()).isEqualTo("INR");
    assertThat(savedTransaction.getAmount()).isEqualByComparingTo("250.00");
    assertThat(savedOutboxEvent.getEventType()).isEqualTo(KafkaTopics.PAYMENT_INITIATED);
    assertThat(savedOutboxEvent.getAggregateId()).isEqualTo(savedTransaction.getId().toString());
    assertThat(response.transactionReference()).isEqualTo("TXN17100000000001234");
    assertThat(response.status()).isEqualTo(com.bankflow.common.domain.TransactionStatus.PENDING);
  }

  @Test
  @DisplayName("A duplicate idempotency key already cached in Redis should return the cached response without touching the database")
  void initiateTransfer_duplicateIdempotencyKey_shouldReturnCachedWithoutSaving() throws Exception {
    // Arrange
    PaymentService paymentService = new PaymentService(
        transactionRepository,
        org.mockito.Mockito.mock(PaymentTransactionWriter.class),
        new PaymentMapper(),
        redisTemplate,
        objectMapper,
        24);

    TransferRequest request = new TransferRequest(
        UUID.randomUUID(),
        UUID.randomUUID(),
        new BigDecimal("99.00"),
        "INR",
        "Duplicate replay");
    TransferResponse cachedResponse = new TransferResponse(
        UUID.randomUUID(),
        "TXN17100000000009999",
        com.bankflow.common.domain.TransactionStatus.PENDING,
        com.bankflow.common.domain.SagaStatus.STARTED,
        new BigDecimal("99.00"),
        "INR",
        LocalDateTime.now());
    String idempotencyKey = "idem-cached";

    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.get(CacheKeys.IDEMPOTENCY_PREFIX + idempotencyKey))
        .thenReturn(objectMapper.writeValueAsString(cachedResponse));

    // Act
    TransferResponse response = paymentService.initiateTransfer(request, idempotencyKey);

    // Assert
    // This catches the expensive duplicate-processing bug where client retries create multiple
    // transfer rows instead of replaying the first accepted response.
    verify(transactionRepository, never()).saveAndFlush(any(Transaction.class));
    verify(outboxEventRepository, never()).save(any(OutboxEvent.class));
    assertThat(response).isEqualTo(cachedResponse);
  }

  @Test
  @DisplayName("A negative transfer amount should be rejected before any durable write occurs")
  void initiateTransfer_negativeAmount_shouldThrowValidationException() {
    // Arrange
    PaymentService paymentService = new PaymentService(
        transactionRepository,
        org.mockito.Mockito.mock(PaymentTransactionWriter.class),
        new PaymentMapper(),
        redisTemplate,
        objectMapper,
        24);
    TransferRequest request = new TransferRequest(
        UUID.randomUUID(),
        UUID.randomUUID(),
        new BigDecimal("-10.00"),
        "INR",
        "Invalid amount");

    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.get(any(String.class))).thenReturn(null);
    when(transactionRepository.findByIdempotencyKey(any(String.class))).thenReturn(Optional.empty());

    // Act + Assert
    // This prevents a bug where invalid negative amounts are allowed into the saga and later force
    // downstream services to guess how to handle impossible business data.
    assertThatThrownBy(() -> paymentService.initiateTransfer(request, "idem-negative"))
        .isInstanceOf(ValidationException.class)
        .hasMessage("Amount must be positive");

    verify(transactionRepository, never()).saveAndFlush(any(Transaction.class));
  }

  @Test
  @DisplayName("A transfer to the same source and destination account should be rejected as invalid input")
  void initiateTransfer_sameFromAndToAccount_shouldThrowValidationException() {
    // Arrange
    PaymentService paymentService = new PaymentService(
        transactionRepository,
        org.mockito.Mockito.mock(PaymentTransactionWriter.class),
        new PaymentMapper(),
        redisTemplate,
        objectMapper,
        24);
    UUID accountId = UUID.randomUUID();
    TransferRequest request = new TransferRequest(
        accountId,
        accountId,
        new BigDecimal("50.00"),
        "INR",
        "Self transfer");

    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.get(any(String.class))).thenReturn(null);
    when(transactionRepository.findByIdempotencyKey(any(String.class))).thenReturn(Optional.empty());

    // Act + Assert
    // This catches the validation hole where a service would accept nonsense transfers and create
    // misleading audit history for a payment that should never exist.
    assertThatThrownBy(() -> paymentService.initiateTransfer(request, "idem-self"))
        .isInstanceOf(ValidationException.class)
        .hasMessage("Cannot transfer to same account");

    verify(transactionRepository, never()).saveAndFlush(any(Transaction.class));
  }
}
