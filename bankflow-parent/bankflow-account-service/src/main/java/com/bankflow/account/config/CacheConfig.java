package com.bankflow.account.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

/**
 * Redis cache configuration for account-service read models.
 *
 * <p>Plain English: this config defines named caches with different TTLs for account documents and
 * balance snapshots.
 *
 * <p>Design decision: JSON serialization is used instead of Java serialization because Java object
 * serialization is brittle when classes evolve, while JSON is easier to inspect and more tolerant
 * of additive fields.
 *
 * <p>Interview question answered: "How do you configure Redis caching safely for a CQRS service?"
 */
@Configuration
@EnableCaching
public class CacheConfig {

  private final long accountCacheTtlSeconds;
  private final long balanceCacheTtlSeconds;

  public CacheConfig(
      @Value("${cache.account.ttl}") long accountCacheTtlSeconds,
      @Value("${cache.balance.ttl}") long balanceCacheTtlSeconds) {
    this.accountCacheTtlSeconds = accountCacheTtlSeconds;
    this.balanceCacheTtlSeconds = balanceCacheTtlSeconds;
  }

  /**
   * Creates a cache manager with dedicated TTLs for account and balance caches.
   */
  @Bean
  public RedisCacheManager redisCacheManager(
      RedisConnectionFactory connectionFactory,
      ObjectMapper objectMapper) {
    GenericJackson2JsonRedisSerializer serializer =
        new GenericJackson2JsonRedisSerializer(objectMapper.findAndRegisterModules());

    RedisCacheConfiguration baseConfiguration = RedisCacheConfiguration.defaultCacheConfig()
        .computePrefixWith(cacheName -> "bankflow::" + cacheName + "::")
        .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(serializer));

    return RedisCacheManager.builder(connectionFactory)
        .cacheDefaults(baseConfiguration)
        .withCacheConfiguration(
            "accounts",
            baseConfiguration.entryTtl(Duration.ofSeconds(accountCacheTtlSeconds)))
        .withCacheConfiguration(
            "balances",
            baseConfiguration.entryTtl(Duration.ofSeconds(balanceCacheTtlSeconds)))
        .transactionAware()
        .build();
  }
}
