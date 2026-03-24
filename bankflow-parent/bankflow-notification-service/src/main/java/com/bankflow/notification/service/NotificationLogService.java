package com.bankflow.notification.service;

import com.bankflow.common.domain.NotificationType;
import com.bankflow.notification.domain.NotificationStatus;
import com.bankflow.notification.entity.NotificationLog;
import com.bankflow.notification.repository.NotificationLogRepository;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Writes durable notification audit rows.
 *
 * <p>Plain English: this service centralizes how the notification service records successes,
 * failures, retry attempts, and DLT events so support and compliance teams see consistent data.
 */
@Service
public class NotificationLogService {

  private static final int CONTENT_LIMIT = 2000;
  private static final int FAILURE_REASON_LIMIT = 1000;

  private final NotificationLogRepository notificationLogRepository;

  public NotificationLogService(NotificationLogRepository notificationLogRepository) {
    this.notificationLogRepository = notificationLogRepository;
  }

  public void log(
      UUID userId,
      NotificationType type,
      String recipient,
      String subject,
      String content,
      NotificationStatus status,
      String referenceId,
      String eventType,
      Integer retryCount,
      String failureReason,
      LocalDateTime sentAt) {
    NotificationLog notificationLog = new NotificationLog();
    notificationLog.setId(UUID.randomUUID());
    notificationLog.setUserId(userId);
    notificationLog.setType(type);
    notificationLog.setRecipient(truncate(recipient, 255));
    notificationLog.setSubject(truncate(subject, 255));
    notificationLog.setContent(truncate(content, CONTENT_LIMIT));
    notificationLog.setStatus(status);
    notificationLog.setReferenceId(truncate(referenceId, 64));
    notificationLog.setEventType(truncate(eventType, 120));
    notificationLog.setRetryCount(retryCount == null ? 0 : retryCount);
    notificationLog.setFailureReason(truncate(failureReason, FAILURE_REASON_LIMIT));
    notificationLog.setSentAt(sentAt);
    notificationLogRepository.save(notificationLog);
  }

  private String truncate(String value, int maxLength) {
    if (value == null) {
      return null;
    }
    return value.length() <= maxLength ? value : value.substring(0, maxLength);
  }
}
