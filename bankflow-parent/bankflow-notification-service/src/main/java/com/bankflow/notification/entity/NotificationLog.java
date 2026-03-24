package com.bankflow.notification.entity;

import com.bankflow.common.domain.NotificationType;
import com.bankflow.notification.domain.NotificationStatus;
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
 * Durable audit log for every notification attempt.
 *
 * <p>Plain English: this gives support and compliance teams a permanent record of which event tried
 * to notify which recipient, whether it succeeded, and why it failed if it did not.
 *
 * <p>Design decision: content is truncated before persistence so large HTML bodies do not bloat the
 * database while still leaving enough context for debugging and audits.
 */
@Getter
@Setter
@ToString
@NoArgsConstructor
@Entity
@Table(name = "notification_logs")
@EntityListeners(AuditingEntityListener.class)
public class NotificationLog {

  @Id
  private UUID id;

  @Column
  private UUID userId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private NotificationType type;

  @Column(nullable = false, length = 255)
  private String recipient;

  @Column(nullable = false, length = 255)
  private String subject;

  @Column(nullable = false, length = 2000)
  private String content;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private NotificationStatus status;

  @Column(nullable = false, length = 64)
  private String referenceId;

  @Column(nullable = false, length = 120)
  private String eventType;

  @CreatedDate
  @Column(nullable = false, updatable = false)
  private LocalDateTime createdAt;

  private LocalDateTime sentAt;

  @Column(nullable = false)
  private int retryCount = 0;

  @Column(length = 1000)
  private String failureReason;
}
