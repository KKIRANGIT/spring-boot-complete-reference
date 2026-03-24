package com.bankflow.notification.service;

import com.bankflow.common.event.AccountCreatedEvent;
import com.bankflow.common.event.PaymentCompletedEvent;
import com.bankflow.common.event.PaymentReversedEvent;
import org.springframework.stereotype.Component;

/**
 * Resolves a recipient address for local notification delivery.
 *
 * <p>Plain English: upstream events in the current BankFlow build do not yet carry real email
 * addresses, so local development derives deterministic MailHog inboxes from stable ids instead of
 * blocking notification development on another service.
 *
 * <p>Design decision: the generated addresses are explicit and obviously non-production so this
 * fallback cannot accidentally send real emails while still letting developers verify templates and
 * workflows in MailHog.
 */
@Component
public class NotificationRecipientResolver {

  public String resolvePaymentRecipient(PaymentCompletedEvent event) {
    return "account-" + shortId(event.fromAccountId().toString()) + "@bankflow.local";
  }

  public String resolvePaymentRecipient(PaymentReversedEvent event) {
    return "account-" + shortId(event.fromAccountId().toString()) + "@bankflow.local";
  }

  public String resolveAccountRecipient(AccountCreatedEvent event) {
    return "user-" + shortId(event.userId().toString()) + "@bankflow.local";
  }

  private String shortId(String value) {
    return value.substring(Math.max(0, value.length() - 8));
  }
}
