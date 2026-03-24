package com.bankflow.notification.service;

import com.bankflow.common.event.AccountCreatedEvent;
import com.bankflow.common.event.PaymentCompletedEvent;
import com.bankflow.common.event.PaymentReversedEvent;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

/**
 * HTML email delivery service for notification-service.
 *
 * <p>Plain English: this renders Thymeleaf templates and sends them through the configured SMTP
 * server. In local development MailHog catches the messages, so developers can verify the exact
 * email body at `http://localhost:8025` without sending real mail.
 */
@Service
public class EmailService {

  private static final DateTimeFormatter TIMESTAMP_FORMATTER =
      DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm:ss");

  private final JavaMailSender mailSender;
  private final SpringTemplateEngine templateEngine;
  private final NotificationRecipientResolver notificationRecipientResolver;

  public EmailService(
      JavaMailSender mailSender,
      SpringTemplateEngine templateEngine,
      NotificationRecipientResolver notificationRecipientResolver) {
    this.mailSender = mailSender;
    this.templateEngine = templateEngine;
    this.notificationRecipientResolver = notificationRecipientResolver;
  }

  public EmailPayload sendPaymentSuccessEmail(PaymentCompletedEvent event) throws MessagingException {
    String recipient = notificationRecipientResolver.resolvePaymentRecipient(event);
    String subject = "BankFlow: Transfer Successful - Ref: " + event.transactionReference();

    Context context = new Context();
    context.setVariable("amount", event.amount());
    context.setVariable("toAccount", maskAccountNumber(event.toAccountId().toString()));
    context.setVariable("reference", event.transactionReference());
    context.setVariable("timestamp", formatTimestamp(event.occurredAt()));
    String html = templateEngine.process("email/payment-success", context);

    sendHtmlEmail(recipient, subject, html);
    return new EmailPayload(recipient, subject, html);
  }

  public EmailPayload sendPaymentReversedEmail(PaymentReversedEvent event) throws MessagingException {
    String recipient = notificationRecipientResolver.resolvePaymentRecipient(event);
    String subject = "BankFlow: Transfer Reversed - Ref: " + event.transactionReference();

    Context context = new Context();
    context.setVariable("amount", event.amount());
    context.setVariable("fromAccount", maskAccountNumber(event.fromAccountId().toString()));
    context.setVariable("reference", event.transactionReference());
    context.setVariable("timestamp", formatTimestamp(event.occurredAt()));
    String html = templateEngine.process("email/payment-reversed", context);

    sendHtmlEmail(recipient, subject, html);
    return new EmailPayload(recipient, subject, html);
  }

  public EmailPayload sendAccountCreatedEmail(AccountCreatedEvent event) throws MessagingException {
    String recipient = notificationRecipientResolver.resolveAccountRecipient(event);
    String subject = "BankFlow: Account Created - " + event.accountNumber();

    Context context = new Context();
    context.setVariable("accountNumber", maskAccountNumber(event.accountNumber()));
    context.setVariable("accountType", event.accountType());
    context.setVariable("balance", event.balance());
    context.setVariable("timestamp", formatTimestamp(event.occurredAt()));
    String html = templateEngine.process("email/account-created", context);

    sendHtmlEmail(recipient, subject, html);
    return new EmailPayload(recipient, subject, html);
  }

  private void sendHtmlEmail(String recipient, String subject, String html) throws MessagingException {
    MimeMessage message = mailSender.createMimeMessage();
    MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
    helper.setFrom("noreply@bankflow.local");
    helper.setTo(recipient);
    helper.setSubject(subject);
    helper.setText(html, true);
    mailSender.send(message);
  }

  private String maskAccountNumber(String accountNumber) {
    if (accountNumber == null || accountNumber.length() <= 4) {
      return accountNumber;
    }
    return "*".repeat(Math.max(0, accountNumber.length() - 4)) + accountNumber.substring(accountNumber.length() - 4);
  }

  private String formatTimestamp(LocalDateTime timestamp) {
    return timestamp == null ? "N/A" : timestamp.format(TIMESTAMP_FORMATTER);
  }

  /**
   * Returned to the Kafka listener so the exact sent content can be truncated and persisted.
   */
  public record EmailPayload(String recipient, String subject, String htmlContent) {
  }
}
