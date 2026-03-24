package com.bankflow.notification.config;

import com.bankflow.common.kafka.KafkaTopics;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Kafka consumer wiring for notification-service with manual acknowledgments and dead-letter
 * publishing.
 *
 * <p>Plain English: this keeps notifications at-least-once by acknowledging only after successful
 * processing, and it stops poison messages from looping forever by sending them to `topic.DLT`
 * after three retries spaced two seconds apart.
 */
@Configuration
public class KafkaConsumerConfig {

  @Bean
  public ProducerFactory<String, String> dltProducerFactory(KafkaProperties kafkaProperties) {
    Map<String, Object> properties = new HashMap<>(kafkaProperties.buildProducerProperties());
    properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    return new DefaultKafkaProducerFactory<>(properties);
  }

  @Bean
  public KafkaTemplate<String, String> dltKafkaTemplate(ProducerFactory<String, String> dltProducerFactory) {
    return new KafkaTemplate<>(dltProducerFactory);
  }

  @Bean
  public ConsumerFactory<String, String> consumerFactory(
      KafkaProperties kafkaProperties,
      @Value("${notification.kafka.group-id}") String groupId) {
    Map<String, Object> properties = new HashMap<>(kafkaProperties.buildConsumerProperties());
    properties.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
    properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
    properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    return new DefaultKafkaConsumerFactory<>(properties);
  }

  @Bean
  public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
      ConsumerFactory<String, String> consumerFactory,
      KafkaTemplate<String, String> dltKafkaTemplate,
      @Value("${notification.kafka.retry.backoff-ms}") long backoffMs,
      @Value("${notification.kafka.retry.max-attempts}") long maxAttempts) {
    ConcurrentKafkaListenerContainerFactory<String, String> factory =
        new ConcurrentKafkaListenerContainerFactory<>();
    factory.setConsumerFactory(consumerFactory);
    factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);

    DefaultErrorHandler errorHandler = new DefaultErrorHandler(
        new DeadLetterPublishingRecoverer(
            dltKafkaTemplate,
            (record, ex) -> new TopicPartition(record.topic() + KafkaTopics.DLT_SUFFIX, record.partition())),
        new FixedBackOff(backoffMs, maxAttempts));
    factory.setCommonErrorHandler(errorHandler);
    return factory;
  }
}
