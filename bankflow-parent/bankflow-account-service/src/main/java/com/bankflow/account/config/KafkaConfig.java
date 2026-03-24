package com.bankflow.account.config;

import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
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
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

/**
 * Kafka producer and consumer configuration for account-service saga traffic.
 *
 * <p>Plain English: this makes sure account-service publishes JSON events and consumes them with
 * manual acknowledgments.
 */
@Configuration
public class KafkaConfig {

  /**
   * Builds the producer factory used for BankFlow domain events.
   */
  @Bean
  public ProducerFactory<String, Object> producerFactory(KafkaProperties kafkaProperties) {
    Map<String, Object> properties = new HashMap<>(kafkaProperties.buildProducerProperties());
    properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
    return new DefaultKafkaProducerFactory<>(properties);
  }

  /**
   * Creates the Kafka template shared by account event publishers.
   */
  @Bean
  public KafkaTemplate<String, Object> kafkaTemplate(ProducerFactory<String, Object> producerFactory) {
    return new KafkaTemplate<>(producerFactory);
  }

  /**
   * Builds the consumer factory for polymorphic BankFlow events.
   */
  @Bean
  public ConsumerFactory<String, Object> consumerFactory(KafkaProperties kafkaProperties) {
    Map<String, Object> properties = new HashMap<>(kafkaProperties.buildConsumerProperties());
    properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
    JsonDeserializer<Object> valueDeserializer = new JsonDeserializer<>();
    valueDeserializer.addTrustedPackages("com.bankflow.common.event");

    return new DefaultKafkaConsumerFactory<>(
        properties,
        new StringDeserializer(),
        valueDeserializer);
  }

  /**
   * Creates a manual-ack Kafka listener container factory.
   */
  @Bean
  public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory(
      ConsumerFactory<String, Object> consumerFactory) {
    ConcurrentKafkaListenerContainerFactory<String, Object> factory =
        new ConcurrentKafkaListenerContainerFactory<>();
    factory.setConsumerFactory(consumerFactory);
    factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
    return factory;
  }
}
