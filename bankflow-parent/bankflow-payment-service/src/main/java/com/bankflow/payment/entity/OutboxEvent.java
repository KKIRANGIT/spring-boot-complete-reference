package com.bankflow.payment.entity;

import com.bankflow.payment.domain.OutboxStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Problem this class solves - THE DUAL WRITE PROBLEM:
 *
 * <p>Naive approach (WRONG):
 *
 * <pre>
 * @Transactional
 * transactionRepository.save(transaction)   // Step 1: DB save
 * kafkaTemplate.send(topic, event)          // Step 2: Kafka publish
 * </pre>
 *
 * <p>What can go wrong:
 *
 * <p>Scenario A: Step 1 succeeds, service crashes before Step 2. Transaction is in DB and Kafka
 * never gets the event. Payment is stuck and money can appear frozen.
 *
 * <p>Scenario B: Step 2 succeeds, Step 1 fails. Kafka has the event but no Transaction record.
 * Account-service might debit money while payment-service has nothing to show the user.
 *
 * <p>The problem: DB and Kafka are two separate systems. You cannot make one atomic commit across
 * both with normal application code.
 *
 * <p>Solution - Outbox Pattern:
 *
 * <pre>
 * @Transactional {
 *   transactionRepository.save(transaction)   // Step 1: DB save
 *   outboxRepository.save(outboxEvent)        // Step 2: SAME TX
 * }
 * </pre>
 *
 * <p>Both rows commit or both roll back. A separate scheduled publisher reads pending outbox rows,
 * publishes them to Kafka, and marks them published. If the scheduler crashes, the rows are still
 * pending and will be retried after restart. This gives at-least-once Kafka delivery with database
 * consistency.
 */
@Getter
@Setter
@NoArgsConstructor
@ToString
@Entity
@Table(name = "outbox_events")
@EntityListeners(AuditingEntityListener.class)
public class OutboxEvent {

  @Id
  private UUID id;

  @Column(nullable = false, length = 40)
  private String aggregateId;

  @Column(nullable = false, length = 40)
  private String aggregateType = "TRANSACTION";

  @Column(nullable = false, length = 120)
  private String eventType;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String payload;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private OutboxStatus status = OutboxStatus.PENDING;

  @CreatedDate
  @Column(nullable = false, updatable = false)
  private LocalDateTime createdAt;

  private LocalDateTime publishedAt;

  @Column(nullable = false)
  private int retryCount = 0;

  @Column(length = 1000)
  private String lastError;
}
