package com.bankflow.common.cache;

import java.util.UUID;

/**
 * Redis key naming convention for BankFlow.
 *
 * <p>Design decision: cache keys are namespaced so multiple services can share Redis without
 * collisions or ambiguous ownership.
 *
 * <p>Interview answer: this class shows how to design cache hygiene for a microservice platform.
 *
 * <p>Bug/security issue prevented: ad hoc keys cause collisions, stale reads, and make it harder
 * to safely delete auth or payment-related data by prefix.
 */
public final class CacheKeys {

  public static final String ACCOUNT_PREFIX = "bankflow:account:";
  public static final String BALANCE_PREFIX = "bankflow:balance:";
  public static final String BLACKLIST_PREFIX = "blacklist:token:";
  public static final String IDEMPOTENCY_PREFIX = "idempotency:";
  public static final String NOTIFICATION_PROCESSED_PREFIX = "notification:processed:";

  private CacheKeys() {
    // Utility class.
  }

  /**
   * Creates the canonical account cache key.
   */
  public static String account(UUID id) {
    return ACCOUNT_PREFIX + id;
  }
}
