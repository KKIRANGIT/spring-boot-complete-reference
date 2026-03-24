package com.bankflow.account.service;

import com.bankflow.account.dto.AccountBalanceResponse;
import com.bankflow.account.dto.AccountResponse;
import com.bankflow.account.dto.AccountStatementEntryResponse;
import com.bankflow.account.entity.Account;
import com.bankflow.account.repository.AccountAuditLogRepository;
import com.bankflow.account.repository.AccountBalanceView;
import com.bankflow.account.repository.AccountRepository;
import com.bankflow.common.exception.ResourceNotFoundException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CQRS read-side service for account queries.
 *
 * <p>Plain English: this service handles all read operations and decides which queries are cacheable
 * and which must always hit the database.
 *
 * <p>Interview question answered: "How do you separate read and write concerns in a CQRS account
 * service with Redis caching?"
 */
@Service
@Transactional(readOnly = true)
public class AccountQueryService {

  private final AccountRepository accountRepository;
  private final AccountAuditLogRepository accountAuditLogRepository;
  private final AccountViewMapper accountViewMapper;

  public AccountQueryService(
      AccountRepository accountRepository,
      AccountAuditLogRepository accountAuditLogRepository,
      AccountViewMapper accountViewMapper) {
    this.accountRepository = accountRepository;
    this.accountAuditLogRepository = accountAuditLogRepository;
    this.accountViewMapper = accountViewMapper;
  }

  /**
   * Returns one account by id, using the five-minute account cache.
   */
  @Cacheable(value = "accounts", key = "#id.toString()")
  public AccountResponse getAccountById(UUID id) {
    Account account = accountRepository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Account", "id", id));
    return accountViewMapper.toAccountResponse(account);
  }

  /**
   * Returns the latest balance snapshot using a short-lived cache.
   */
  @Cacheable(value = "balances", key = "#accountId.toString()")
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

  /**
   * Returns all accounts for one user and intentionally bypasses caching.
   */
  public List<AccountResponse> getAccountsByUserId(UUID userId) {
    return accountRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
        .map(accountViewMapper::toAccountResponse)
        .toList();
  }

  /**
   * Returns the latest statement lines directly from the audit table.
   */
  public Page<AccountStatementEntryResponse> getAccountStatement(UUID accountId, Pageable pageable) {
    accountRepository.findById(accountId)
        .orElseThrow(() -> new ResourceNotFoundException("Account", "id", accountId));
    return accountAuditLogRepository.findByAccountIdOrderByPerformedAtDesc(accountId, pageable)
        .map(accountViewMapper::toStatementEntry);
  }
}
