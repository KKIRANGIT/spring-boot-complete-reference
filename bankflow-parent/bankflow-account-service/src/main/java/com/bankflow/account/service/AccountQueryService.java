package com.bankflow.account.service;

import com.bankflow.account.dto.AccountBalanceResponse;
import com.bankflow.account.dto.AccountResponse;
import com.bankflow.account.dto.AccountStatementEntryResponse;
import com.bankflow.account.entity.Account;
import com.bankflow.account.repository.AccountAuditLogRepository;
import com.bankflow.account.repository.AccountBalanceView;
import com.bankflow.account.repository.AccountRepository;
import com.bankflow.common.exception.ResourceNotFoundException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AccountQueryService {

  private final AccountRepository accountRepository;
  private final AccountAuditLogRepository accountAuditLogRepository;
  private final AccountViewMapper accountViewMapper;
  private final CacheManager cacheManager;
  private final Counter cacheHits;
  private final Counter cacheMisses;

  public AccountQueryService(
      AccountRepository accountRepository,
      AccountAuditLogRepository accountAuditLogRepository,
      AccountViewMapper accountViewMapper) {
    this(
        accountRepository,
        accountAuditLogRepository,
        accountViewMapper,
        new ConcurrentMapCacheManager("accounts", "balances"),
        new SimpleMeterRegistry());
  }

  @Autowired
  public AccountQueryService(
      AccountRepository accountRepository,
      AccountAuditLogRepository accountAuditLogRepository,
      AccountViewMapper accountViewMapper,
      CacheManager cacheManager,
      MeterRegistry meterRegistry) {
    this.accountRepository = accountRepository;
    this.accountAuditLogRepository = accountAuditLogRepository;
    this.accountViewMapper = accountViewMapper;
    this.cacheManager = cacheManager;
    this.cacheHits = Counter.builder("bankflow.accounts.cache.hits")
        .description("Account details cache hits")
        .register(meterRegistry);
    this.cacheMisses = Counter.builder("bankflow.accounts.cache.misses")
        .description("Account details cache misses")
        .register(meterRegistry);
  }

  public AccountResponse getAccountById(UUID id) {
    Cache accountCache = cacheManager.getCache("accounts");
    if (accountCache != null) {
      AccountResponse cachedResponse = accountCache.get(id.toString(), AccountResponse.class);
      if (cachedResponse != null) {
        cacheHits.increment();
        return cachedResponse;
      }
    }

    cacheMisses.increment();
    Account account = accountRepository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Account", "id", id));
    AccountResponse response = accountViewMapper.toAccountResponse(account);
    if (accountCache != null) {
      accountCache.put(id.toString(), response);
    }
    return response;
  }

  @Cacheable(value = "balances", key = "#a0.toString()")
  public AccountBalanceResponse getBalance(UUID accountId) {
    AccountBalanceView balanceView = accountRepository.findBalanceById(accountId)
        .orElseThrow(() -> new ResourceNotFoundException("Account", "id", accountId));
    return new AccountBalanceResponse(
        balanceView.getAccountId(),
        balanceView.getBalance(),
        balanceView.getCurrency(),
        balanceView.getStatus(),
        LocalDateTime.now());
  }

  public List<AccountResponse> getAccountsByUserId(UUID userId) {
    return accountRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
        .map(accountViewMapper::toAccountResponse)
        .toList();
  }

  public Page<AccountStatementEntryResponse> getAccountStatement(UUID accountId, Pageable pageable) {
    accountRepository.findById(accountId)
        .orElseThrow(() -> new ResourceNotFoundException("Account", "id", accountId));
    return accountAuditLogRepository.findByAccountIdOrderByPerformedAtDesc(accountId, pageable)
        .map(accountViewMapper::toStatementEntry);
  }
}
