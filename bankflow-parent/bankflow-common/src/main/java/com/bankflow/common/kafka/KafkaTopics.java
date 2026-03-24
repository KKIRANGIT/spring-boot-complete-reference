package com.bankflow.common.kafka;

/**
 * Central topic name registry for BankFlow integration events.
 *
 * <p>Design decision: Kafka topic names live in one class instead of being duplicated in producer
 * and consumer annotations across services.
 *
 * <p>Interview answer: this class demonstrates how to keep distributed event contracts explicit
 * and discoverable.
 *
 * <p>Bug prevented: string duplication in Kafka code leads to silent consumer drift and missed
 * events when one side changes a topic name by accident.
 */
public final class KafkaTopics {

  public static final String PAYMENT_INITIATED = "bankflow.payment.initiated";
  public static final String ACCOUNT_DEBITED = "bankflow.account.debited";
  public static final String ACCOUNT_CREDITED = "bankflow.account.credited";
  public static final String ACCOUNT_DEBIT_FAILED = "bankflow.account.debit.failed";
  public static final String ACCOUNT_CREDIT_FAILED = "bankflow.account.credit.failed";
  public static final String COMPENSATION_REQUESTED = "bankflow.compensation.requested";
  public static final String ACCOUNT_REVERSAL_COMPLETED = "bankflow.account.reversal.completed";
  public static final String PAYMENT_COMPLETED = "bankflow.payment.completed";
  public static final String PAYMENT_REVERSED = "bankflow.payment.reversed";
  public static final String PAYMENT_FAILED = "bankflow.payment.failed";
  public static final String ACCOUNT_CREATED = "bankflow.account.created";
  public static final String DLT_SUFFIX = ".DLT";

  private KafkaTopics() {
    // Utility class.
  }
}
