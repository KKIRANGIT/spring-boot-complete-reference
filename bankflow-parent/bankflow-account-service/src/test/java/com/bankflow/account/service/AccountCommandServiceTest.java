package com.bankflow.account.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bankflow.account.dto.AccountResponse;
import com.bankflow.account.entity.Account;
import com.bankflow.account.entity.AccountAuditLog;
import com.bankflow.account.repository.AccountAuditLogRepository;
import com.bankflow.account.repository.AccountRepository;
import com.bankflow.account.service.command.CreateAccountCommand;
import com.bankflow.account.service.command.CreditAccountCommand;
import com.bankflow.account.service.command.DebitAccountCommand;
import com.bankflow.account.service.command.UpdateAccountStatusCommand;
import com.bankflow.common.domain.AccountStatus;
import com.bankflow.common.domain.AccountType;
import com.bankflow.common.event.AccountCreatedEvent;
import com.bankflow.common.event.AccountCreditedEvent;
import com.bankflow.common.event.AccountDebitedEvent;
import com.bankflow.common.exception.AccountFrozenException;
import com.bankflow.common.exception.InsufficientFundsException;
import com.bankflow.common.kafka.KafkaTopics;
import com.bankflow.common.util.DataMaskingUtil;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * Unit tests for {@link AccountCommandService}.
 *
 * <p>Plain English: these tests isolate the account write side and prove that debits, credits,
 * account creation, status changes, audit logging, Kafka publication, and optimistic-lock retries
 * behave correctly.
 *
 * <p>Design decision: a tiny Spring context is used here so {@code @Retryable} is exercised through
 * the real retry proxy while everything expensive stays mocked.
 *
 * <p>Bug prevented: optimistic-lock retries often look correct in source code but never execute in
 * tests unless the service is actually wrapped in Spring retry infrastructure.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = AccountCommandServiceTest.TestConfig.class)
class AccountCommandServiceTest {

  private static final UUID ACCOUNT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
  private static final UUID TRANSACTION_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

  @Autowired
  private AccountCommandService accountCommandService;

  @Autowired
  private AccountRepository accountRepository;

  @Autowired
  private AccountAuditLogRepository accountAuditLogRepository;

  @Autowired
  private AccountNumberGenerator accountNumberGenerator;

  @Autowired
  private KafkaTemplate<String, Object> kafkaTemplate;

  @Autowired
  private CacheManager cacheManager;

  @BeforeEach
  void setUp() {
    when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(accountAuditLogRepository.save(any(AccountAuditLog.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(accountNumberGenerator.generate()).thenReturn("BNK123456789");
  }

  @AfterEach
  void tearDown() {
    reset(accountRepository, accountAuditLogRepository, accountNumberGenerator, kafkaTemplate);
    if (cacheManager.getCache("accounts") != null) {
      cacheManager.getCache("accounts").clear();
    }
    if (cacheManager.getCache("balances") != null) {
      cacheManager.getCache("balances").clear();
    }
  }

  @Test
  @DisplayName("Debit with sufficient funds should lower the balance, save the audit row, and publish ACCOUNT_DEBITED")
  void debit_withSufficientFunds_shouldUpdateBalanceAndSaveAuditAndPublishEvent() {
    // Arrange
    Account account = buildAccount(ACCOUNT_ID, new BigDecimal("1000.00"), AccountStatus.ACTIVE);
    when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));
    ArgumentCaptor<Account> accountCaptor = ArgumentCaptor.forClass(Account.class);
    ArgumentCaptor<AccountAuditLog> auditCaptor = ArgumentCaptor.forClass(AccountAuditLog.class);
    DebitAccountCommand command = new DebitAccountCommand(
        ACCOUNT_ID,
        new BigDecimal("300.00"),
        TRANSACTION_ID,
        USER_ID,
        "ATM cash withdrawal");

    // Act
    AccountResponse response = accountCommandService.debitAccount(command);

    // Assert
    verify(accountRepository).save(accountCaptor.capture());
    verify(accountAuditLogRepository).save(auditCaptor.capture());
    verify(kafkaTemplate).send(
        org.mockito.ArgumentMatchers.eq(KafkaTopics.ACCOUNT_DEBITED),
        org.mockito.ArgumentMatchers.eq(ACCOUNT_ID.toString()),
        any(AccountDebitedEvent.class));
    assertThat(accountCaptor.getValue().getBalance()).isEqualByComparingTo("700.00");
    assertThat(auditCaptor.getValue().getPreviousBalance()).isEqualByComparingTo("1000.00");
    assertThat(auditCaptor.getValue().getNewBalance()).isEqualByComparingTo("700.00");
    assertThat(auditCaptor.getValue().getAmount()).isEqualByComparingTo("300.00");
    assertThat(response.balance()).isEqualByComparingTo("700.00");
  }

  @Test
  @DisplayName("Debit with insufficient funds should fail before any save so balances never go negative")
  void debit_withInsufficientFunds_shouldThrowAndNotSave() {
    // Arrange
    Account account = buildAccount(ACCOUNT_ID, new BigDecimal("100.00"), AccountStatus.ACTIVE);
    when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));
    DebitAccountCommand command = new DebitAccountCommand(
        ACCOUNT_ID,
        new BigDecimal("500.00"),
        TRANSACTION_ID,
        USER_ID,
        "Large transfer");

    // Act + Assert
    assertThatThrownBy(() -> accountCommandService.debitAccount(command))
        .isInstanceOf(InsufficientFundsException.class)
        .hasMessageContaining("Requested: 500.00");
    verify(accountRepository, never()).save(any(Account.class));
    verify(accountAuditLogRepository, never()).save(any(AccountAuditLog.class));
    verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
  }

  @Test
  @DisplayName("Debit from a frozen account should be rejected so blocked accounts cannot move money")
  void debit_withFrozenAccount_shouldThrow() {
    // Arrange
    Account account = buildAccount(ACCOUNT_ID, new BigDecimal("1000.00"), AccountStatus.FROZEN);
    when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));
    DebitAccountCommand command = new DebitAccountCommand(
        ACCOUNT_ID,
        new BigDecimal("100.00"),
        TRANSACTION_ID,
        USER_ID,
        "Frozen account debit");

    // Act + Assert
    assertThatThrownBy(() -> accountCommandService.debitAccount(command))
        .isInstanceOf(AccountFrozenException.class)
        .hasMessageContaining(ACCOUNT_ID.toString());
    verify(accountRepository, never()).save(any(Account.class));
    verify(accountAuditLogRepository, never()).save(any(AccountAuditLog.class));
  }

  @Test
  @DisplayName("Debit should retry once and succeed when the first save hits an optimistic-lock conflict")
  void debit_withOptimisticLockConflict_shouldRetryAndSucceed() {
    // Arrange
    when(accountRepository.findById(ACCOUNT_ID))
        .thenReturn(
            Optional.of(buildAccount(ACCOUNT_ID, new BigDecimal("1000.00"), AccountStatus.ACTIVE)),
            Optional.of(buildAccount(ACCOUNT_ID, new BigDecimal("1000.00"), AccountStatus.ACTIVE)));
    when(accountRepository.save(any(Account.class)))
        .thenThrow(new ObjectOptimisticLockingFailureException(Account.class, ACCOUNT_ID))
        .thenAnswer(invocation -> invocation.getArgument(0));
    DebitAccountCommand command = new DebitAccountCommand(
        ACCOUNT_ID,
        new BigDecimal("300.00"),
        TRANSACTION_ID,
        USER_ID,
        "Retry debit");

    // Act
    AccountResponse response = accountCommandService.debitAccount(command);

    // Assert
    verify(accountRepository, times(2)).save(any(Account.class));
    verify(accountAuditLogRepository, times(1)).save(any(AccountAuditLog.class));
    verify(kafkaTemplate, times(1)).send(
        org.mockito.ArgumentMatchers.eq(KafkaTopics.ACCOUNT_DEBITED),
        org.mockito.ArgumentMatchers.eq(ACCOUNT_ID.toString()),
        any(AccountDebitedEvent.class));
    assertThat(response.balance()).isEqualByComparingTo("700.00");
  }

  @Test
  @DisplayName("Debit should stop after three optimistic-lock conflicts so callers can surface contention cleanly")
  void debit_withThreeConsecutiveLockConflicts_shouldThrowAfterMaxRetries() {
    // Arrange
    when(accountRepository.findById(ACCOUNT_ID))
        .thenReturn(
            Optional.of(buildAccount(ACCOUNT_ID, new BigDecimal("1000.00"), AccountStatus.ACTIVE)),
            Optional.of(buildAccount(ACCOUNT_ID, new BigDecimal("1000.00"), AccountStatus.ACTIVE)),
            Optional.of(buildAccount(ACCOUNT_ID, new BigDecimal("1000.00"), AccountStatus.ACTIVE)));
    when(accountRepository.save(any(Account.class)))
        .thenThrow(new ObjectOptimisticLockingFailureException(Account.class, ACCOUNT_ID))
        .thenThrow(new ObjectOptimisticLockingFailureException(Account.class, ACCOUNT_ID))
        .thenThrow(new ObjectOptimisticLockingFailureException(Account.class, ACCOUNT_ID));
    DebitAccountCommand command = new DebitAccountCommand(
        ACCOUNT_ID,
        new BigDecimal("300.00"),
        TRANSACTION_ID,
        USER_ID,
        "Retry exhaustion");

    // Act + Assert
    assertThatThrownBy(() -> accountCommandService.debitAccount(command))
        .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    verify(accountRepository, times(3)).save(any(Account.class));
    verify(accountAuditLogRepository, never()).save(any(AccountAuditLog.class));
    verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
  }

  @Test
  @DisplayName("Create account should persist the account, write an audit row, and publish ACCOUNT_CREATED")
  void createAccount_withValidCommand_shouldPersistAccountAuditAndEvent() {
    // Arrange
    when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> {
      Account saved = invocation.getArgument(0);
      saved.setCreatedAt(LocalDateTime.now());
      saved.setUpdatedAt(LocalDateTime.now());
      return saved;
    });
    ArgumentCaptor<Account> accountCaptor = ArgumentCaptor.forClass(Account.class);
    CreateAccountCommand command = new CreateAccountCommand(
        USER_ID,
        AccountType.SAVINGS,
        new BigDecimal("5000.00"),
        "inr",
        USER_ID);

    // Act
    AccountResponse response = accountCommandService.createAccount(command);

    // Assert
    verify(accountRepository).save(accountCaptor.capture());
    verify(accountAuditLogRepository).save(any(AccountAuditLog.class));
    verify(kafkaTemplate).send(
        org.mockito.ArgumentMatchers.eq(KafkaTopics.ACCOUNT_CREATED),
        anyString(),
        any(AccountCreatedEvent.class));
    assertThat(accountCaptor.getValue().getAccountNumber()).isEqualTo("BNK123456789");
    assertThat(accountCaptor.getValue().getCurrency()).isEqualTo("INR");
    assertThat(response.accountNumber()).isEqualTo("BNK123456789");
    assertThat(response.balance()).isEqualByComparingTo("5000.00");
  }

  @Test
  @DisplayName("Credit should increase the balance, save an audit row, and publish ACCOUNT_CREDITED")
  void credit_withActiveAccount_shouldIncreaseBalanceAndPublishEvent() {
    // Arrange
    Account account = buildAccount(ACCOUNT_ID, new BigDecimal("1000.00"), AccountStatus.ACTIVE);
    when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));
    CreditAccountCommand command = new CreditAccountCommand(
        ACCOUNT_ID,
        new BigDecimal("250.00"),
        TRANSACTION_ID,
        USER_ID,
        "Salary credit");

    // Act
    AccountResponse response = accountCommandService.creditAccount(command);

    // Assert
    verify(accountAuditLogRepository).save(any(AccountAuditLog.class));
    verify(kafkaTemplate).send(
        org.mockito.ArgumentMatchers.eq(KafkaTopics.ACCOUNT_CREDITED),
        org.mockito.ArgumentMatchers.eq(ACCOUNT_ID.toString()),
        any(AccountCreditedEvent.class));
    assertThat(response.balance()).isEqualByComparingTo("1250.00");
  }

  @Test
  @DisplayName("Status updates should persist the new lifecycle state and record an audit trail")
  void updateStatus_shouldPersistNewStatusAndAudit() {
    // Arrange
    Account account = buildAccount(ACCOUNT_ID, new BigDecimal("1000.00"), AccountStatus.ACTIVE);
    when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));
    ArgumentCaptor<Account> accountCaptor = ArgumentCaptor.forClass(Account.class);

    // Act
    AccountResponse response = accountCommandService.updateAccountStatus(new UpdateAccountStatusCommand(
        ACCOUNT_ID,
        AccountStatus.CLOSED,
        USER_ID,
        "Customer requested closure"));

    // Assert
    verify(accountRepository).save(accountCaptor.capture());
    verify(accountAuditLogRepository).save(any(AccountAuditLog.class));
    assertThat(accountCaptor.getValue().getStatus()).isEqualTo(AccountStatus.CLOSED);
    assertThat(response.status()).isEqualTo(AccountStatus.CLOSED);
  }

  private Account buildAccount(UUID accountId, BigDecimal balance, AccountStatus status) {
    Account account = new Account();
    account.setId(accountId);
    account.setAccountNumber("BNK123456789");
    account.setUserId(USER_ID);
    account.setAccountType(AccountType.SAVINGS);
    account.setBalance(balance);
    account.setCurrency("INR");
    account.setStatus(status);
    account.setCreatedAt(LocalDateTime.now().minusDays(1));
    account.setUpdatedAt(LocalDateTime.now().minusMinutes(1));
    account.setVersion(0L);
    return account;
  }

  @Configuration
  @EnableRetry(proxyTargetClass = true)
  static class TestConfig {

    @Bean
    AccountRepository accountRepository() {
      return mock(AccountRepository.class);
    }

    @Bean
    AccountAuditLogRepository accountAuditLogRepository() {
      return mock(AccountAuditLogRepository.class);
    }

    @Bean
    AccountNumberGenerator accountNumberGenerator() {
      return mock(AccountNumberGenerator.class);
    }

    @Bean
    KafkaTemplate<String, Object> kafkaTemplate() {
      return mock(KafkaTemplate.class);
    }

    @Bean
    AccountEventPublisher accountEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
      return new AccountEventPublisher(kafkaTemplate);
    }

    @Bean
    AccountViewMapper accountViewMapper() {
      return new AccountViewMapper();
    }

    @Bean
    CacheManager cacheManager() {
      return new ConcurrentMapCacheManager("accounts", "balances");
    }

    @Bean
    DataMaskingUtil dataMaskingUtil() {
      return new DataMaskingUtil();
    }

    @Bean
    AccountCommandService accountCommandService(
        AccountRepository accountRepository,
        AccountAuditLogRepository accountAuditLogRepository,
        AccountNumberGenerator accountNumberGenerator,
        AccountEventPublisher accountEventPublisher,
        AccountViewMapper accountViewMapper,
        CacheManager cacheManager,
        DataMaskingUtil dataMaskingUtil) {
      return new AccountCommandService(
          accountRepository,
          accountAuditLogRepository,
          accountNumberGenerator,
          accountEventPublisher,
          accountViewMapper,
          cacheManager,
          dataMaskingUtil);
    }
  }
}
