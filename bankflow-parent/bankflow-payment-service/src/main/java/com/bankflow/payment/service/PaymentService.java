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
import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Payment-service application layer for transfer initiation and transaction queries.
 *
 * <p>Plain English: this class handles request idempotency, business validation, durable transfer
 * creation through the outbox writer, and transaction lookups.
 */
@Service
public class PaymentService {

  private final TransactionRepository transactionRepository;
  private final PaymentTransactionWriter paymentTransactionWriter;
  private final PaymentMapper paymentMapper;
  private final StringRedisTemplate redisTemplate;
  private final ObjectMapper objectMapper;
  private final Duration idempotencyTtl;

  public PaymentService(
      TransactionRepository transactionRepository,
      PaymentTransactionWriter paymentTransactionWriter,
      PaymentMapper paymentMapper,
      StringRedisTemplate redisTemplate,
      ObjectMapper objectMapper,
      @Value("${payment.idempotency.ttl-hours:24}") long idempotencyTtlHours) {
    this.transactionRepository = transactionRepository;
    this.paymentTransactionWriter = paymentTransactionWriter;
    this.paymentMapper = paymentMapper;
    this.redisTemplate = redisTemplate;
    this.objectMapper = objectMapper;
    this.idempotencyTtl = Duration.ofHours(idempotencyTtlHours);
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
