package com.bankflow.account.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bankflow.account.dto.AccountBalanceResponse;
import com.bankflow.account.dto.AccountResponse;
import com.bankflow.account.dto.AccountStatementEntryResponse;
import com.bankflow.account.entity.Account;
import com.bankflow.account.entity.AccountAuditLog;
import com.bankflow.account.repository.AccountAuditLogRepository;
import com.bankflow.account.repository.AccountBalanceView;
import com.bankflow.account.repository.AccountRepository;
import com.bankflow.common.domain.AccountStatus;
import com.bankflow.common.domain.AccountType;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = AccountQueryServiceTest.TestConfig.class)
class AccountQueryServiceTest {

  private static final UUID ACCOUNT_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
  private static final UUID USER_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

  @Autowired private AccountQueryService accountQueryService;
  @Autowired private AccountRepository accountRepository;
  @Autowired private AccountAuditLogRepository accountAuditLogRepository;
  @Autowired private CacheManager cacheManager;

  @AfterEach
  void tearDown() {
    reset(accountRepository, accountAuditLogRepository);
    cacheManager.getCache("accounts").clear();
    cacheManager.getCache("balances").clear();
  }

  @Test
  @DisplayName("Get account by id should return a pre-cached document without touching the repository")
  void getById_whenCacheHit_shouldNotCallRepository() {
    AccountResponse cachedResponse = new AccountResponse(
        ACCOUNT_ID,
        "BNK123456789",
        USER_ID,
        AccountType.SAVINGS,
        new BigDecimal("1500.00"),
        "INR",
        AccountStatus.ACTIVE,
        LocalDateTime.now().minusDays(2),
        LocalDateTime.now().minusMinutes(2));
    cacheManager.getCache("accounts").put(ACCOUNT_ID.toString(), cachedResponse);

    AccountResponse response = accountQueryService.getAccountById(ACCOUNT_ID);

    verify(accountRepository, never()).findById(ACCOUNT_ID);
    assertThat(response).isEqualTo(cachedResponse);
  }

  @Test
  @DisplayName("Get account by id should load once on a cache miss and then reuse the cached response")
  void getById_whenCacheMiss_shouldCallRepositoryAndPopulateCache() {
    Account account = buildAccount();
    when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));

    AccountResponse firstCall = accountQueryService.getAccountById(ACCOUNT_ID);
    AccountResponse secondCall = accountQueryService.getAccountById(ACCOUNT_ID);

    verify(accountRepository, times(1)).findById(ACCOUNT_ID);
    assertThat(firstCall.accountNumber()).isEqualTo("BNK123456789");
    assertThat(secondCall).isEqualTo(firstCall);
  }

  @Test
  @DisplayName("Get balance should cache the projection response so repeated balance reads stay cheap")
  void getBalance_shouldCacheProjectionResponse() {
    when(accountRepository.findBalanceById(ACCOUNT_ID)).thenReturn(Optional.of(balanceView()));

    AccountBalanceResponse firstCall = accountQueryService.getBalance(ACCOUNT_ID);
    AccountBalanceResponse secondCall = accountQueryService.getBalance(ACCOUNT_ID);

    verify(accountRepository, times(1)).findBalanceById(ACCOUNT_ID);
    assertThat(firstCall.balance()).isEqualByComparingTo("8800.00");
    assertThat(secondCall.balance()).isEqualByComparingTo("8800.00");
  }

  @Test
  @DisplayName("Get accounts by user should bypass the cache because the list can change as accounts are created")
  void getAccountsByUserId_shouldReturnMappedResultsWithoutCaching() {
    when(accountRepository.findByUserIdOrderByCreatedAtDesc(USER_ID))
        .thenReturn(List.of(buildAccount(), buildSecondAccount()));

    List<AccountResponse> response = accountQueryService.getAccountsByUserId(USER_ID);

    verify(accountRepository).findByUserIdOrderByCreatedAtDesc(USER_ID);
    assertThat(response).hasSize(2);
  }

  @Test
  @DisplayName("Get statement should read directly from the audit log table so the latest ledger entries are always visible")
  void getAccountStatement_shouldReturnPagedAuditRows() {
    AccountAuditLog auditLog = new AccountAuditLog();
    auditLog.setId(UUID.randomUUID());
    auditLog.setAccountId(ACCOUNT_ID);
    auditLog.setAction("DEBITED");
    auditLog.setPreviousBalance(new BigDecimal("1000.00"));
    auditLog.setNewBalance(new BigDecimal("700.00"));
    auditLog.setAmount(new BigDecimal("300.00"));
    auditLog.setPerformedBy(USER_ID);
    auditLog.setPerformedAt(LocalDateTime.now().minusMinutes(5));
    auditLog.setDescription("ATM withdrawal");
    when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(buildAccount()));
    when(accountAuditLogRepository.findByAccountIdOrderByPerformedAtDesc(
        ACCOUNT_ID,
        PageRequest.of(0, 20)))
        .thenReturn(new PageImpl<>(List.of(auditLog)));

    Page<AccountStatementEntryResponse> response =
        accountQueryService.getAccountStatement(ACCOUNT_ID, PageRequest.of(0, 20));

    verify(accountAuditLogRepository).findByAccountIdOrderByPerformedAtDesc(ACCOUNT_ID, PageRequest.of(0, 20));
    assertThat(response.getContent()).hasSize(1);
  }

  private Account buildAccount() {
    Account account = new Account();
    account.setId(ACCOUNT_ID);
    account.setAccountNumber("BNK123456789");
    account.setUserId(USER_ID);
    account.setAccountType(AccountType.SAVINGS);
    account.setBalance(new BigDecimal("1500.00"));
    account.setCurrency("INR");
    account.setStatus(AccountStatus.ACTIVE);
    account.setCreatedAt(LocalDateTime.now().minusDays(2));
    account.setUpdatedAt(LocalDateTime.now().minusMinutes(2));
    return account;
  }

  private Account buildSecondAccount() {
    Account account = new Account();
    account.setId(UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc"));
    account.setAccountNumber("BNK987654321");
    account.setUserId(USER_ID);
    account.setAccountType(AccountType.CURRENT);
    account.setBalance(new BigDecimal("9000.00"));
    account.setCurrency("INR");
    account.setStatus(AccountStatus.ACTIVE);
    account.setCreatedAt(LocalDateTime.now().minusDays(1));
    account.setUpdatedAt(LocalDateTime.now().minusMinutes(1));
    return account;
  }

  private AccountBalanceView balanceView() {
    return new AccountBalanceView() {
      @Override public UUID getAccountId() { return ACCOUNT_ID; }
      @Override public BigDecimal getBalance() { return new BigDecimal("8800.00"); }
      @Override public String getCurrency() { return "INR"; }
      @Override public AccountStatus getStatus() { return AccountStatus.ACTIVE; }
    };
  }

  @Configuration
  @EnableCaching
  static class TestConfig {
    @Bean AccountRepository accountRepository() { return mock(AccountRepository.class); }
    @Bean AccountAuditLogRepository accountAuditLogRepository() { return mock(AccountAuditLogRepository.class); }
    @Bean AccountViewMapper accountViewMapper() { return new AccountViewMapper(); }
    @Bean CacheManager cacheManager() { return new ConcurrentMapCacheManager("accounts", "balances"); }
    @Bean MeterRegistry meterRegistry() { return new SimpleMeterRegistry(); }
    @Bean
    AccountQueryService accountQueryService(
        AccountRepository accountRepository,
        AccountAuditLogRepository accountAuditLogRepository,
        AccountViewMapper accountViewMapper,
        CacheManager cacheManager,
        MeterRegistry meterRegistry) {
      return new AccountQueryService(accountRepository, accountAuditLogRepository, accountViewMapper, cacheManager, meterRegistry);
    }
  }
}
