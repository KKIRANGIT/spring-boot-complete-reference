package com.bankflow.payment.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.bankflow.common.domain.SagaStatus;
import com.bankflow.common.domain.TransactionStatus;
import com.bankflow.common.event.AccountCreditedEvent;
import com.bankflow.common.event.AccountDebitedEvent;
import com.bankflow.common.kafka.KafkaTopics;
import com.bankflow.payment.BankflowPaymentServiceApplication;
import com.bankflow.payment.domain.OutboxStatus;
import com.bankflow.payment.dto.TransferRequest;
import com.bankflow.payment.dto.TransferResponse;
import com.bankflow.payment.entity.OutboxEvent;
import com.bankflow.payment.entity.Transaction;
import com.bankflow.payment.repository.OutboxEventRepository;
import com.bankflow.payment.repository.TransactionRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Integration tests for payment-service against real MySQL, Redis, and Kafka containers.
 *
 * <p>Plain English: these tests prove the outbox pattern, idempotency cache, and Kafka-driven saga
 * transitions work together across the full Spring Boot application.
 */
@SpringBootTest(
    classes = BankflowPaymentServiceApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class PaymentSagaIT {

  @Container
  static final MySQLContainer<?> MYSQL =
      new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
          .withDatabaseName("bankflow_payment")
          .withUsername("root")
          .withPassword("bankflow_root");

  @Container
  static final GenericContainer<?> REDIS =
      new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
          .withCommand("redis-server", "--requirepass", "bankflow_redis")
          .withExposedPorts(6379);

  @Container
  static final KafkaContainer KAFKA =
      new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

  @Autowired
  private PaymentService paymentService;

  @Autowired
  private OutboxPublisher outboxPublisher;

  @Autowired
  private TransactionRepository transactionRepository;

  @Autowired
  private OutboxEventRepository outboxEventRepository;

  @Autowired
  private KafkaTemplate<String, Object> kafkaTemplate;

  @Autowired
  private StringRedisTemplate redisTemplate;

  @DynamicPropertySource
  static void registerProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
    registry.add("spring.datasource.username", MYSQL::getUsername);
    registry.add("spring.datasource.password", MYSQL::getPassword);
    registry.add("spring.data.redis.host", REDIS::getHost);
    registry.add("spring.data.redis.port", REDIS::getFirstMappedPort);
    registry.add("spring.data.redis.password", () -> "bankflow_redis");
    registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    registry.add("payment.outbox.fixed-delay-ms", () -> "600000");
  }

  @AfterEach
  void cleanState() {
    outboxEventRepository.deleteAll();
    transactionRepository.deleteAll();
  }

  @Test
  @DisplayName("A happy-path saga should move from initiated to account debited to completed")
  void happyPathSaga_shouldCompleteTransaction() throws Exception {
    // Arrange
    TransferResponse response = paymentService.initiateTransfer(
        new TransferRequest(
            UUID.randomUUID(),
            UUID.randomUUID(),
            new BigDecimal("150.00"),
            "INR",
            "Integration transfer"),
        "it-key-" + UUID.randomUUID());

    // Act
    // This is the critical end-to-end proof that the outbox row is durable first and only then
    // moves through Kafka-driven saga transitions into a completed payment record.
    List<OutboxEvent> initialOutboxEvents = outboxEventRepository.findAll();
    outboxPublisher.publishPendingEvents();
    OutboxEvent publishedEvent = outboxEventRepository.findAll().get(0);

    kafkaTemplate.send(
            KafkaTopics.ACCOUNT_DEBITED,
            response.transactionId().toString(),
            new AccountDebitedEvent(
                UUID.randomUUID(),
                response.transactionId(),
                UUID.randomUUID(),
                new BigDecimal("150.00"),
                new BigDecimal("850.00"),
                LocalDateTime.now()))
        .get(10, TimeUnit.SECONDS);
    Transaction debitedTransaction = awaitTransaction(
        response.transactionId(),
        transaction -> transaction.getSagaStatus() == SagaStatus.ACCOUNT_DEBITED);

    kafkaTemplate.send(
            KafkaTopics.ACCOUNT_CREDITED,
            response.transactionId().toString(),
            new AccountCreditedEvent(
                UUID.randomUUID(),
                response.transactionId(),
                UUID.randomUUID(),
                new BigDecimal("150.00"),
                new BigDecimal("1150.00"),
                LocalDateTime.now()))
        .get(10, TimeUnit.SECONDS);
    Transaction completedTransaction = awaitTransaction(
        response.transactionId(),
        transaction -> transaction.getStatus() == TransactionStatus.COMPLETED);

    // Assert
    assertThat(initialOutboxEvents).hasSize(1);
    assertThat(initialOutboxEvents.get(0).getStatus()).isEqualTo(OutboxStatus.PENDING);
    assertThat(publishedEvent.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
    assertThat(debitedTransaction.getSagaStatus()).isEqualTo(SagaStatus.ACCOUNT_DEBITED);
    assertThat(completedTransaction.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
    assertThat(completedTransaction.getSagaStatus()).isEqualTo(SagaStatus.COMPLETED);
    assertThat(completedTransaction.getCompletedAt()).isNotNull();
  }

  @Test
  @DisplayName("Reusing the same idempotency key should create only one transaction and replay the same response")
  void idempotencyTest_duplicateKey_onlyOneTransactionCreated() {
    // Arrange
    String idempotencyKey = "test-key-001-" + UUID.randomUUID();
    TransferRequest request = new TransferRequest(
        UUID.randomUUID(),
        UUID.randomUUID(),
        new BigDecimal("220.00"),
        "INR",
        "Idempotent transfer");

    // Act
    TransferResponse firstResponse = paymentService.initiateTransfer(request, idempotencyKey);
    TransferResponse secondResponse = paymentService.initiateTransfer(request, idempotencyKey);
    String cachedJson = redisTemplate.opsForValue().get("idempotency:" + idempotencyKey);

    // Assert
    // This catches the duplicate-payment bug where a client retry under network uncertainty creates
    // two transaction rows instead of replaying the first accepted payment response.
    assertThat(transactionRepository.count()).isEqualTo(1);
    assertThat(outboxEventRepository.count()).isEqualTo(1);
    assertThat(secondResponse.transactionReference()).isEqualTo(firstResponse.transactionReference());
    assertThat(secondResponse.transactionId()).isEqualTo(firstResponse.transactionId());
    assertThat(cachedJson).contains(firstResponse.transactionReference());
  }

  private Transaction awaitTransaction(UUID transactionId, Predicate<Transaction> condition) throws InterruptedException {
    long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
    while (System.nanoTime() < deadline) {
      Transaction transaction = transactionRepository.findById(transactionId).orElseThrow();
      if (condition.test(transaction)) {
        return transaction;
      }
      Thread.sleep(200);
    }
    return transactionRepository.findById(transactionId).orElseThrow();
  }
}
