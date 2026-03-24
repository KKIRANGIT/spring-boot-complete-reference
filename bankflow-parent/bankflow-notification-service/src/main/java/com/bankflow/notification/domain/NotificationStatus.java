package com.bankflow.notification.domain;

/**
 * Delivery lifecycle for one notification attempt.
 *
 * <p>Plain English: a notification starts pending, becomes sent after MailHog or a real SMTP
 * provider accepts it, or ends as failed after retries and DLT handling exhaust the workflow.
 */
public enum NotificationStatus {
  SENT,
  FAILED,
  PENDING
}
