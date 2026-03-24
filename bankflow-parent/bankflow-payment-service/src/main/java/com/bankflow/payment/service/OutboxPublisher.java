package com.bankflow.payment.service;

import com.bankflow.common.event.AccountCreditRequestedEvent;
import com.bankflow.common.event.CompensationRequestedEvent;
import com.bankflow.common.event.PaymentCompletedEvent;
import com.bankflow.common.event.PaymentFailedEvent;
import com.bankflow.common.event.PaymentInitiatedEvent;
import com.bankflow.common.event.PaymentReversedEvent;
import com.bankflow.common.kafka.KafkaTopics;
import com.bankflow.payment.domain.OutboxStatus;
import com.bankflow.payment.entity.OutboxEvent;
import com.bankflow.payment.repository.OutboxEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Scheduled publisher that drains pending outbox rows into Kafka.
 *
 * <p>Plain English: this is the second half of the outbox pattern. It turns durable pending rows
 * into Kafka events and retries safely after crashes.
 */
@Component
public class OutboxPublisher {

  private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

  private final OutboxEventRepository outboxEventRepository;
  private final KafkaTemplate<String, Object> kafkaTemplate;
  private final ObjectMapper objectMapper;
  private final int batchSize;

  public OutboxPublisher(
      OutboxEventRepository outboxEventRepository,
      KafkaTemplate<String, Object> kafkaTemplate,
      ObjectMapper objectMapper,
      @Value("${payment.outbox.batch-size:100}") int batchSize) {
    this.outboxEventRepository = outboxEventRepository;
    this.kafkaTemplate = kafkaTemplate;
    this.objectMapper = objectMapper;
    this.batchSize = batchSize;
  }

  @Scheduled(fixedDelayString = "${payment.outbox.fixed-delay-ms:1000}")
  @Transactional
  public void publishPendingEvents() {
    List<OutboxEvent> pendingEvents = outboxEventRepository.findByStatusAndRetryCountLessThanOrderByCreatedAtAsc(
        OutboxStatus.PENDING,
        3,
        PageRequest.of(0, batchSize));

    for (OutboxEvent event : pendingEvents) {
      try {
        String topic = resolveTopic(event.getEventType());
        Object payload = deserializePayload(event);
        kafkaTemplate.send(topic, event.getAggregateId(), payload).get(5, TimeUnit.SECONDS);
        event.setStatus(OutboxStatus.PUBLISHED);
        event.setPublishedAt(LocalDateTime.now());
      } catch (Exception ex) {
        event.setRetryCount(event.getRetryCount() + 1);
        event.setLastError(ex.getMessage());
        if (event.getRetryCount() >= 3) {
          event.setStatus(OutboxStatus.FAILED);
          log.error("Outbox event permanently failed: {}", event.getId(), ex);
        }
      }
      outboxEventRepository.save(event);
    }
  }

  private String resolveTopic(String eventType) {
    return switch (eventType) {
      case KafkaTopics.PAYMENT_INITIATED,
          KafkaTopics.ACCOUNT_CREDIT_REQUESTED,
          KafkaTopics.COMPENSATION_REQUESTED,
          KafkaTopics.PAYMENT_COMPLETED,
          KafkaTopics.PAYMENT_FAILED,
          KafkaTopics.PAYMENT_REVERSED -> eventType;
      default -> throw new IllegalStateException("Unsupported outbox topic: " + eventType);
    };
  }

  private Object deserializePayload(OutboxEvent event) {
    try {
      return switch (event.getEventType()) {
        case KafkaTopics.PAYMENT_INITIATED -> objectMapper.readValue(event.getPayload(), PaymentInitiatedEvent.class);
        case KafkaTopics.ACCOUNT_CREDIT_REQUESTED ->
            objectMapper.readValue(event.getPayload(), AccountCreditRequestedEvent.class);
        case KafkaTopics.COMPENSATION_REQUESTED ->
            objectMapper.readValue(event.getPayload(), CompensationRequestedEvent.class);
        case KafkaTopics.PAYMENT_COMPLETED ->
            objectMapper.readValue(event.getPayload(), PaymentCompletedEvent.class);
        case KafkaTopics.PAYMENT_FAILED ->
            objectMapper.readValue(event.getPayload(), PaymentFailedEvent.class);
        case KafkaTopics.PAYMENT_REVERSED ->
            objectMapper.readValue(event.getPayload(), PaymentReversedEvent.class);
        default -> throw new IllegalStateException("Unsupported outbox payload type: " + event.getEventType());
      };
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("Failed to deserialize outbox payload for event " + event.getId(), ex);
    }
  }
}
