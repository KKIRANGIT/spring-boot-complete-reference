package com.bankflow.account.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.ContainerProperties;

/**
 * Focused tests for account-service infrastructure configuration classes.
 *
 * <p>Plain English: these tests cover the cache and Kafka wiring code so configuration regressions
 * are caught before the service starts on a developer machine.
 */
class AccountConfigTest {

  @Test
  @DisplayName("Cache config should build Redis caches for accounts and balances with JSON serialization")
  void cacheConfig_shouldCreateRedisCacheManager() {
    // Arrange
    CacheConfig cacheConfig = new CacheConfig(300L, 30L);
    RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);
    ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    // Act
    RedisCacheManager cacheManager = cacheConfig.redisCacheManager(connectionFactory, objectMapper);

    // Assert
    assertThat(cacheManager).isNotNull();
    assertThat(cacheManager.getCache("accounts")).isNotNull();
    assertThat(cacheManager.getCache("balances")).isNotNull();
  }

  @Test
  @DisplayName("Kafka config should create producer, consumer, template, and manual-ack listener factory")
  void kafkaConfig_shouldCreateProducerConsumerAndListenerFactory() {
    // Arrange
    KafkaConfig kafkaConfig = new KafkaConfig();
    KafkaProperties kafkaProperties = new KafkaProperties();
    kafkaProperties.setBootstrapServers(List.of("localhost:9092"));

    // Act
    ProducerFactory<String, Object> producerFactory = kafkaConfig.producerFactory(kafkaProperties);
    KafkaTemplate<String, Object> kafkaTemplate = kafkaConfig.kafkaTemplate(producerFactory);
    ConsumerFactory<String, Object> consumerFactory = kafkaConfig.consumerFactory(kafkaProperties);
    ConcurrentKafkaListenerContainerFactory<String, Object> listenerFactory =
        kafkaConfig.kafkaListenerContainerFactory(consumerFactory);

    // Assert
    assertThat(producerFactory).isNotNull();
    assertThat(kafkaTemplate).isNotNull();
    assertThat(consumerFactory).isNotNull();
    assertThat(listenerFactory.getContainerProperties().getAckMode())
        .isEqualTo(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
  }
}
