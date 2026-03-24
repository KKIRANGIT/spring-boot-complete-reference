package com.bankflow.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bankflow.common.event.PaymentInitiatedEvent;
import com.bankflow.common.kafka.KafkaTopics;
import com.bankflow.payment.domain.OutboxStatus;
import com.bankflow.payment.entity.OutboxEvent;
import com.bankflow.payment.repository.OutboxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

/**
 * Unit tests for {@link OutboxPublisher}.
 *
 * <p>Plain English: these tests prove pending outbox rows are promoted to published only after a
 * Kafka acknowledgment arrives and are retried safely when publishing fails.
 */
@ExtendWith(MockitoExtension.class)
class OutboxPublisherTest {

  @Test
  @DisplayName("A pending outbox event should publish to Kafka and then be marked published with a timestamp")
  void pendingEvent_shouldPublishToKafkaAndMarkPublished() throws Exception {
    // Arrange
    OutboxEventRepository outboxEventRepository = org.mockito.Mockito.mock(OutboxEventRepository.class);
    @SuppressWarnings("unchecked")
    KafkaTemplate<String, Object> kafkaTemplate = org.mockito.Mockito.mock(KafkaTemplate.class);
    OutboxPublisher outboxPublisher = new OutboxPublisher(
        outboxEventRepository,
        kafkaTemplate,
        new ObjectMapper().registerModule(new JavaTimeModule()),
        100);

    OutboxEvent event = pendingPaymentInitiatedEvent();
    when(outboxEventRepository.findByStatusAndRetryCountLessThanOrderByCreatedAtAsc(
        OutboxStatus.PENDING,
        3,
        PageRequest.of(0, 100))).thenReturn(List.of(event));
    when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(successfulFuture());

    // Act
    outboxPublisher.publishPendingEvents();

    // Assert
    // This catches the consistency bug where an outbox row is marked published before Kafka has
    // actually acknowledged the record, which would lose events during broker outages.
    verify(kafkaTemplate).send(anyString(), anyString(), any());
    verify(outboxEventRepository).save(event);
    assertThat(event.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
    assertThat(event.getPublishedAt()).isNotNull();
  }

  @Test
  @DisplayName("A Kafka publish failure should increment retry count and leave the event pending for the next scheduler run")
  void kafkaFailure_shouldIncrementRetryCount() throws Exception {
    // Arrange
    OutboxEventRepository outboxEventRepository = org.mockito.Mockito.mock(OutboxEventRepository.class);
    @SuppressWarnings("unchecked")
    KafkaTemplate<String, Object> kafkaTemplate = org.mockito.Mockito.mock(KafkaTemplate.class);
    OutboxPublisher outboxPublisher = new OutboxPublisher(
        outboxEventRepository,
        kafkaTemplate,
        new ObjectMapper().registerModule(new JavaTimeModule()),
        100);

    OutboxEvent event = pendingPaymentInitiatedEvent();
    when(outboxEventRepository.findByStatusAndRetryCountLessThanOrderByCreatedAtAsc(
        OutboxStatus.PENDING,
        3,
        PageRequest.of(0, 100))).thenReturn(List.of(event));
    when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(failedFuture("Kafka down"));

    // Act
    outboxPublisher.publishPendingEvents();

    // Assert
    // This prevents the retry bug where one transient Kafka outage would permanently drop the
    // event instead of leaving it pending for another scheduler attempt.
    verify(outboxEventRepository).save(event);
    assertThat(event.getRetryCount()).isEqualTo(1);
    assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
    assertThat(event.getLastError()).contains("Kafka down");
  }

  @Test
  @DisplayName("A third consecutive Kafka failure should mark the outbox event as failed")
  void thirdKafkaFailure_shouldMarkEventFailed() throws Exception {
    // Arrange
    OutboxEventRepository outboxEventRepository = org.mockito.Mockito.mock(OutboxEventRepository.class);
    @SuppressWarnings("unchecked")
    KafkaTemplate<String, Object> kafkaTemplate = org.mockito.Mockito.mock(KafkaTemplate.class);
    OutboxPublisher outboxPublisher = new OutboxPublisher(
        outboxEventRepository,
        kafkaTemplate,
        new ObjectMapper().registerModule(new JavaTimeModule()),
        100);

    OutboxEvent event = pendingPaymentInitiatedEvent();
    event.setRetryCount(2);
    when(outboxEventRepository.findByStatusAndRetryCountLessThanOrderByCreatedAtAsc(
        OutboxStatus.PENDING,
        3,
        PageRequest.of(0, 100))).thenReturn(List.of(event));
    when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(failedFuture("Still down"));

    // Act
    outboxPublisher.publishPendingEvents();

    // Assert
    // This catches the observability bug where permanently failing events remain pending forever
    // and never surface as operational incidents that need intervention.
    verify(outboxEventRepository).save(event);
    assertThat(event.getRetryCount()).isEqualTo(3);
    assertThat(event.getStatus()).isEqualTo(OutboxStatus.FAILED);
  }

  private OutboxEvent pendingPaymentInitiatedEvent() throws Exception {
    OutboxEvent event = new OutboxEvent();
    event.setId(UUID.randomUUID());
    event.setAggregateId(UUID.randomUUID().toString());
    event.setEventType(KafkaTopics.PAYMENT_INITIATED);
    event.setStatus(OutboxStatus.PENDING);
    event.setCreatedAt(LocalDateTime.now().minusMinutes(1));
    event.setPayload(new ObjectMapper().registerModule(new JavaTimeModule()).writeValueAsString(
        new PaymentInitiatedEvent(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            new BigDecimal("150.00"),
            "INR",
            LocalDateTime.now())));
    return event;
  }

  private CompletableFuture<SendResult<String, Object>> successfulFuture() {
    CompletableFuture<SendResult<String, Object>> future = new CompletableFuture<>();
    future.complete(null);
    return future;
  }

  private CompletableFuture<SendResult<String, Object>> failedFuture(String message) {
    CompletableFuture<SendResult<String, Object>> future = new CompletableFuture<>();
    future.completeExceptionally(new RuntimeException(message));
    return future;
  }
}
