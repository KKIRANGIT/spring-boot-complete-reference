package com.bankflow.payment.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.ContainerProperties;

/**
 * Focused tests for payment-service configuration beans.
 *
 * <p>Plain English: these tests catch infrastructure wiring regressions before the service starts.
 */
class PaymentConfigTest {

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
