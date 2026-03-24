package com.bankflow.payment.service;

import com.bankflow.payment.entity.OutboxEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Builds outbox rows from typed domain events.
 *
 * <p>Plain English: this keeps JSON serialization and outbox metadata consistent so every saga step
 * writes outbox rows the same way.
 */
@Component
public class OutboxEventFactory {

  private final ObjectMapper objectMapper;

  public OutboxEventFactory(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public OutboxEvent createTransactionOutboxEvent(UUID aggregateId, String eventType, Object payload) {
    try {
      OutboxEvent outboxEvent = new OutboxEvent();
      outboxEvent.setId(UUID.randomUUID());
      outboxEvent.setAggregateId(aggregateId.toString());
      outboxEvent.setAggregateType("TRANSACTION");
      outboxEvent.setEventType(eventType);
      outboxEvent.setPayload(objectMapper.writeValueAsString(payload));
      return outboxEvent;
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("Failed to serialize outbox payload for event " + eventType, ex);
    }
  }
}
