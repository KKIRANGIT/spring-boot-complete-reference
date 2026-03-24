package com.bankflow.account.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.bankflow.account.BankflowAccountServiceApplication;
import com.bankflow.account.entity.Account;
import com.bankflow.account.repository.AccountRepository;
import com.bankflow.account.service.command.DebitAccountCommand;
import com.bankflow.common.domain.AccountStatus;
import com.bankflow.common.domain.AccountType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
 * Integration test that proves optimistic locking protects the balance under concurrent debits.
 *
 * <p>Plain English: ten threads hit the same account at the same time, and the final balance must
 * reflect exactly ten successful debits with no lost update.
 *
 * <p>Interview question answered: "How do you prove optimistic locking works in a banking service
 * instead of just claiming it does?"
 */
@SpringBootTest(
    classes = BankflowAccountServiceApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class AccountConcurrencyIT {

  @Container
  static final MySQLContainer<?> MYSQL =
      new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
          .withDatabaseName("bankflow_account")
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
  private AccountCommandService accountCommandService;

  @Autowired
  private AccountRepository accountRepository;

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
  }

  @AfterEach
  void cleanDatabase() {
    accountRepository.deleteAll();
  }

  @Test
  @DisplayName("Ten concurrent debits should leave the exact final balance of 9000 with no lost update")
  void tenConcurrentDebits_shouldProduceCorrectFinalBalance() throws Exception {
    // Arrange
    UUID accountId = UUID.randomUUID();
    Account account = new Account();
    account.setId(accountId);
    account.setAccountNumber("BNK765432109");
    account.setUserId(UUID.randomUUID());
    account.setAccountType(AccountType.SAVINGS);
    account.setBalance(new BigDecimal("10000.00"));
    account.setCurrency("INR");
    account.setStatus(AccountStatus.ACTIVE);
    accountRepository.saveAndFlush(account);

    ExecutorService executorService = Executors.newFixedThreadPool(10);
    CountDownLatch start = new CountDownLatch(1);
    CountDownLatch done = new CountDownLatch(10);
    ConcurrentLinkedQueue<Throwable> failures = new ConcurrentLinkedQueue<>();

    // Act
    for (int i = 0; i < 10; i++) {
      executorService.submit(() -> {
        try {
          start.await();
          accountCommandService.debitAccount(new DebitAccountCommand(
              accountId,
              new BigDecimal("100.00"),
              UUID.randomUUID(),
              null,
              "Concurrent debit"));
        } catch (Throwable throwable) {
          failures.add(throwable);
        } finally {
          done.countDown();
        }
      });
    }

    start.countDown();
    boolean completed = done.await(20, TimeUnit.SECONDS);
    executorService.shutdown();
    executorService.awaitTermination(20, TimeUnit.SECONDS);

    // Assert
    Account reloadedAccount = accountRepository.findById(accountId).orElseThrow();
    assertThat(completed).isTrue();
    assertThat(failures).isEmpty();
    assertThat(reloadedAccount.getBalance()).isEqualByComparingTo("9000.00");
  }
}
