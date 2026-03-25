package com.bankflow.payment.service;

import com.bankflow.common.cache.CacheKeys;
import com.bankflow.common.exception.ResourceNotFoundException;
import com.bankflow.common.exception.ValidationException;
import com.bankflow.payment.dto.TransactionResponse;
import com.bankflow.payment.dto.TransferRequest;
import com.bankflow.payment.dto.TransferResponse;
import com.bankflow.payment.entity.Transaction;
import com.bankflow.payment.repository.TransactionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class PaymentService {

  private final TransactionRepository transactionRepository;
  private final PaymentTransactionWriter paymentTransactionWriter;
  private final PaymentMapper paymentMapper;
  private final StringRedisTemplate redisTemplate;
  private final ObjectMapper objectMapper;
  private final Duration idempotencyTtl;
  private final Counter paymentsInitiated;
  private final Counter paymentsCompleted;
  private final Counter paymentsFailed;
  private final Timer paymentDuration;

  public PaymentService(
      TransactionRepository transactionRepository,
      PaymentTransactionWriter paymentTransactionWriter,
      PaymentMapper paymentMapper,
      StringRedisTemplate redisTemplate,
      ObjectMapper objectMapper,
      @Value("${payment.idempotency.ttl-hours:24}") long idempotencyTtlHours) {
    this(
        transactionRepository,
        paymentTransactionWriter,
        paymentMapper,
        redisTemplate,
        objectMapper,
        new SimpleMeterRegistry(),
        idempotencyTtlHours);
  }

  @Autowired
  public PaymentService(
      TransactionRepository transactionRepository,
      PaymentTransactionWriter paymentTransactionWriter,
      PaymentMapper paymentMapper,
      StringRedisTemplate redisTemplate,
      ObjectMapper objectMapper,
      MeterRegistry meterRegistry,
      @Value("${payment.idempotency.ttl-hours:24}") long idempotencyTtlHours) {
    MeterRegistry effectiveRegistry = meterRegistry != null ? meterRegistry : new SimpleMeterRegistry();
    this.transactionRepository = transactionRepository;
    this.paymentTransactionWriter = paymentTransactionWriter;
    this.paymentMapper = paymentMapper;
    this.redisTemplate = redisTemplate;
    this.objectMapper = objectMapper;
    this.idempotencyTtl = Duration.ofHours(idempotencyTtlHours);
    this.paymentsInitiated = Counter.builder("bankflow.payments.initiated")
        .description("Total payment transfers initiated")
        .register(effectiveRegistry);
    this.paymentsCompleted = Counter.builder("bankflow.payments.completed")
        .description("Total payment transfers completed successfully")
        .register(effectiveRegistry);
    this.paymentsFailed = Counter.builder("bankflow.payments.failed")
        .description("Total payment transfers that failed before completion")
        .register(effectiveRegistry);
    this.paymentDuration = Timer.builder("bankflow.payments.duration")
        .description("Time from initiation to completion")
        .publishPercentiles(0.5, 0.95, 0.99)
        .register(effectiveRegistry);
  }

  public TransferResponse initiateTransfer(TransferRequest request, String idempotencyKey) {
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      throw new ValidationException("Idempotency-Key header is required");
    }

    String redisKey = CacheKeys.IDEMPOTENCY_PREFIX + idempotencyKey;
    String cachedResponse = redisTemplate.opsForValue().get(redisKey);
    if (cachedResponse != null) {
      return readTransferResponse(cachedResponse);
    }

    Transaction existingTransaction = transactionRepository.findByIdempotencyKey(idempotencyKey).orElse(null);
    if (existingTransaction != null) {
      TransferResponse existingResponse = paymentMapper.toTransferResponse(existingTransaction);
      cacheTransferResponse(redisKey, existingResponse);
      return existingResponse;
    }

    validateTransferRequest(request);

    Transaction transaction = paymentTransactionWriter.createPendingTransfer(request, idempotencyKey);
    paymentsInitiated.increment();
    TransferResponse response = paymentMapper.toTransferResponse(transaction);
    cacheTransferResponse(redisKey, response);
    return response;
  }

  public TransactionResponse getTransaction(UUID transactionId) {
    return paymentMapper.toTransactionResponse(findTransactionById(transactionId));
  }

  public TransactionResponse getTransactionByReference(String transactionReference) {
    Transaction transaction = transactionRepository.findByTransactionReference(transactionReference)
        .orElseThrow(() -> new ResourceNotFoundException("Transaction", "transactionReference", transactionReference));
    return paymentMapper.toTransactionResponse(transaction);
  }

  void recordPaymentCompleted(Transaction transaction, LocalDateTime completedAt) {
    paymentsCompleted.increment();
    if (transaction.getCreatedAt() == null) {
      return;
    }
    // P99 matters more than averages because tail latency exposes the slowest real users.
    paymentDuration.record(Duration.between(transaction.getCreatedAt(), completedAt));
  }

  void recordPaymentFailed() {
    paymentsFailed.increment();
  }

  private Transaction findTransactionById(UUID transactionId) {
    return transactionRepository.findById(transactionId)
        .orElseThrow(() -> new ResourceNotFoundException("Transaction", "id", transactionId));
  }

  private void validateTransferRequest(TransferRequest request) {
    if (request.amount().compareTo(BigDecimal.ZERO) <= 0) {
      throw new ValidationException("Amount must be positive");
    }
    if (request.fromAccountId().equals(request.toAccountId())) {
      throw new ValidationException("Cannot transfer to same account");
    }
  }

  private TransferResponse readTransferResponse(String cachedResponse) {
    try {
      return objectMapper.readValue(cachedResponse, TransferResponse.class);
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("Failed to deserialize cached transfer response", ex);
    }
  }

  private void cacheTransferResponse(String redisKey, TransferResponse response) {
    try {
      redisTemplate.opsForValue().set(redisKey, objectMapper.writeValueAsString(response), idempotencyTtl);
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("Failed to serialize idempotent transfer response", ex);
    }
  }
}
