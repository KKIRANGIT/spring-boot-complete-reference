package com.bankflow.notification.messaging;

import com.bankflow.common.domain.NotificationType;
import com.bankflow.common.event.AccountCreatedEvent;
import com.bankflow.common.event.PaymentCompletedEvent;
import com.bankflow.common.event.PaymentReversedEvent;
import com.bankflow.common.kafka.KafkaTopics;
import com.bankflow.notification.domain.NotificationStatus;
import com.bankflow.notification.service.EmailService;
import com.bankflow.notification.service.EmailService.EmailPayload;
import com.bankflow.notification.service.NotificationIdempotencyService;
import com.bankflow.notification.service.NotificationLogService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Kafka consumers for payment and account notifications.
 *
 * <p>Plain English: these listeners parse event payloads, suppress duplicate deliveries through
 * Redis, send emails, write durable logs, and let the error handler retry or dead-letter failures.
 */
@Component
public class NotificationKafkaConsumer {

  private static final Logger log = LoggerFactory.getLogger(NotificationKafkaConsumer.class);

  private final ObjectMapper objectMapper;
  private final NotificationIdempotencyService notificationIdempotencyService;
  private final EmailService emailService;
  private final NotificationLogService notificationLogService;

  public NotificationKafkaConsumer(
      ObjectMapper objectMapper,
      NotificationIdempotencyService notificationIdempotencyService,
      EmailService emailService,
      NotificationLogService notificationLogService) {
    this.objectMapper = objectMapper;
    this.notificationIdempotencyService = notificationIdempotencyService;
    this.emailService = emailService;
    this.notificationLogService = notificationLogService;
  }

  @KafkaListener(
      topics = KafkaTopics.PAYMENT_COMPLETED,
      groupId = "${notification.kafka.group-id}",
      containerFactory = "kafkaListenerContainerFactory")
  public void handlePaymentCompleted(
      @Payload String payload,
      @Header(KafkaHeaders.RECEIVED_KEY) String key,
      @Header(name = KafkaHeaders.DELIVERY_ATTEMPT, required = false) Integer deliveryAttempt,
      Acknowledgment acknowledgment) throws Exception {
    PaymentCompletedEvent event = objectMapper.readValue(payload, PaymentCompletedEvent.class);

    if (notificationIdempotencyService.isAlreadyProcessed(event.transactionId().toString())) {
      log.info("Already processed notification for payment completion: {}", event.transactionId());
      acknowledgment.acknowledge();
      return;
    }

    try {
      EmailPayload emailPayload = emailService.sendPaymentSuccessEmail(event);
      notificationLogService.log(
          null,
          NotificationType.EMAIL,
          emailPayload.recipient(),
          emailPayload.subject(),
          emailPayload.htmlContent(),
          NotificationStatus.SENT,
          event.transactionId().toString(),
          KafkaTopics.PAYMENT_COMPLETED,
          normalizedRetryCount(deliveryAttempt),
          null,
          event.occurredAt());
      notificationIdempotencyService.markAsProcessed(event.transactionId().toString());
      acknowledgment.acknowledge();
    } catch (Exception ex) {
      notificationLogService.log(
          null,
          NotificationType.EMAIL,
          "key:" + key,
          "BankFlow payment completion notification failed",
          payload,
          NotificationStatus.FAILED,
          event.transactionId().toString(),
          KafkaTopics.PAYMENT_COMPLETED,
          normalizedRetryCount(deliveryAttempt),
          ex.getMessage(),
          null);
      throw ex;
    }
  }

  @KafkaListener(
      topics = KafkaTopics.PAYMENT_REVERSED,
      groupId = "${notification.kafka.group-id}",
      containerFactory = "kafkaListenerContainerFactory")
  public void handlePaymentReversed(
      @Payload String payload,
      @Header(KafkaHeaders.RECEIVED_KEY) String key,
      @Header(name = KafkaHeaders.DELIVERY_ATTEMPT, required = false) Integer deliveryAttempt,
      Acknowledgment acknowledgment) throws Exception {
    PaymentReversedEvent event = objectMapper.readValue(payload, PaymentReversedEvent.class);

    if (notificationIdempotencyService.isAlreadyProcessed(event.transactionId().toString())) {
      log.info("Already processed notification for payment reversal: {}", event.transactionId());
      acknowledgment.acknowledge();
      return;
    }

    try {
      EmailPayload emailPayload = emailService.sendPaymentReversedEmail(event);
      notificationLogService.log(
          null,
          NotificationType.EMAIL,
          emailPayload.recipient(),
          emailPayload.subject(),
          emailPayload.htmlContent(),
          NotificationStatus.SENT,
          event.transactionId().toString(),
          KafkaTopics.PAYMENT_REVERSED,
          normalizedRetryCount(deliveryAttempt),
          null,
          event.occurredAt());
      notificationIdempotencyService.markAsProcessed(event.transactionId().toString());
      acknowledgment.acknowledge();
    } catch (Exception ex) {
      notificationLogService.log(
          null,
          NotificationType.EMAIL,
          "key:" + key,
          "BankFlow payment reversal notification failed",
          payload,
          NotificationStatus.FAILED,
          event.transactionId().toString(),
          KafkaTopics.PAYMENT_REVERSED,
          normalizedRetryCount(deliveryAttempt),
          ex.getMessage(),
          null);
      throw ex;
    }
  }

  @KafkaListener(
      topics = KafkaTopics.ACCOUNT_CREATED,
      groupId = "${notification.kafka.group-id}",
      containerFactory = "kafkaListenerContainerFactory")
  public void handleAccountCreated(
      @Payload String payload,
      @Header(KafkaHeaders.RECEIVED_KEY) String key,
      @Header(name = KafkaHeaders.DELIVERY_ATTEMPT, required = false) Integer deliveryAttempt,
      Acknowledgment acknowledgment) throws Exception {
    AccountCreatedEvent event = objectMapper.readValue(payload, AccountCreatedEvent.class);

    if (notificationIdempotencyService.isAlreadyProcessed(event.accountId().toString())) {
      log.info("Already processed notification for account creation: {}", event.accountId());
      acknowledgment.acknowledge();
      return;
    }

    try {
      EmailPayload emailPayload = emailService.sendAccountCreatedEmail(event);
      notificationLogService.log(
          event.userId(),
          NotificationType.EMAIL,
          emailPayload.recipient(),
          emailPayload.subject(),
          emailPayload.htmlContent(),
          NotificationStatus.SENT,
          event.accountId().toString(),
          KafkaTopics.ACCOUNT_CREATED,
          normalizedRetryCount(deliveryAttempt),
          null,
          event.occurredAt());
      notificationIdempotencyService.markAsProcessed(event.accountId().toString());
      acknowledgment.acknowledge();
    } catch (Exception ex) {
      notificationLogService.log(
          event.userId(),
          NotificationType.EMAIL,
          "key:" + key,
          "BankFlow account-created notification failed",
          payload,
          NotificationStatus.FAILED,
          event.accountId().toString(),
          KafkaTopics.ACCOUNT_CREATED,
          normalizedRetryCount(deliveryAttempt),
          ex.getMessage(),
          null);
      throw ex;
    }
  }

  @KafkaListener(topics = KafkaTopics.PAYMENT_COMPLETED + KafkaTopics.DLT_SUFFIX)
  public void handlePaymentCompletedDlt(@Payload String payload) {
    log.error("DLT received for payment notification: {}", payload);
    notificationLogService.log(
        null,
        NotificationType.EMAIL,
        "dlt@bankflow.local",
        "BankFlow payment completion notification dead-lettered",
        payload,
        NotificationStatus.FAILED,
        "UNKNOWN",
        KafkaTopics.PAYMENT_COMPLETED + KafkaTopics.DLT_SUFFIX,
        3,
        "DLT received",
        null);
  }

  private int normalizedRetryCount(Integer deliveryAttempt) {
    return deliveryAttempt == null ? 0 : Math.max(0, deliveryAttempt - 1);
  }
}
