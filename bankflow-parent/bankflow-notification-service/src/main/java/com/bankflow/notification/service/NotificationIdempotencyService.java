package com.bankflow.notification.service;

import com.bankflow.common.cache.CacheKeys;
import java.time.Duration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis-backed idempotency guard for Kafka notifications.
 *
 * <p>Plain English: if Kafka redelivers the same business event after a crash or rebalance, this
 * service prevents BankFlow from sending duplicate emails to the customer.
 *
 * <p>Design decision: the 48 hour TTL outlives Kafka's default 24 hour retention so duplicates are
 * still suppressed during the normal replay window for local development and most transient issues.
 */
@Component
public class NotificationIdempotencyService {

  private static final Duration PROCESSED_TTL = Duration.ofHours(48);

  private final StringRedisTemplate redisTemplate;

  public NotificationIdempotencyService(StringRedisTemplate redisTemplate) {
    this.redisTemplate = redisTemplate;
  }

  public boolean isAlreadyProcessed(String eventId) {
    return Boolean.TRUE.equals(redisTemplate.hasKey(CacheKeys.NOTIFICATION_PROCESSED_PREFIX + eventId));
  }

  public void markAsProcessed(String eventId) {
    redisTemplate.opsForValue().set(
        CacheKeys.NOTIFICATION_PROCESSED_PREFIX + eventId,
        "processed",
        PROCESSED_TTL);
  }
}
