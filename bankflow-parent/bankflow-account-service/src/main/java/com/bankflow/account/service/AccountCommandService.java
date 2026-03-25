package com.bankflow.account.service;

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
import com.bankflow.common.event.AccountCreatedEvent;
import com.bankflow.common.event.AccountCreditedEvent;
import com.bankflow.common.event.AccountDebitedEvent;
import com.bankflow.common.exception.AccountFrozenException;
import com.bankflow.common.exception.InsufficientFundsException;
import com.bankflow.common.exception.ResourceNotFoundException;
import com.bankflow.common.util.DataMaskingUtil;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class AccountCommandService {

  private final AccountRepository accountRepository;
  private final AccountAuditLogRepository accountAuditLogRepository;
  private final AccountNumberGenerator accountNumberGenerator;
  private final AccountEventPublisher accountEventPublisher;
  private final AccountViewMapper accountViewMapper;
  private final CacheManager cacheManager;
  private final DataMaskingUtil dataMaskingUtil;

  public AccountCommandService(
      AccountRepository accountRepository,
      AccountAuditLogRepository accountAuditLogRepository,
      AccountNumberGenerator accountNumberGenerator,
      AccountEventPublisher accountEventPublisher,
      AccountViewMapper accountViewMapper,
      CacheManager cacheManager,
      DataMaskingUtil dataMaskingUtil) {
    this.accountRepository = accountRepository;
    this.accountAuditLogRepository = accountAuditLogRepository;
    this.accountNumberGenerator = accountNumberGenerator;
    this.accountEventPublisher = accountEventPublisher;
    this.accountViewMapper = accountViewMapper;
    this.cacheManager = cacheManager;
    this.dataMaskingUtil = dataMaskingUtil;
  }

  @Transactional
  public AccountResponse createAccount(CreateAccountCommand command) {
    Account account = new Account();
    account.setId(UUID.randomUUID());
    account.setAccountNumber(accountNumberGenerator.generate());
    account.setUserId(command.userId());
    account.setAccountType(command.accountType());
    account.setBalance(scale(command.initialDeposit()));
    account.setCurrency(normalizeCurrency(command.currency()));
    account.setStatus(AccountStatus.ACTIVE);

    Account savedAccount = accountRepository.save(account);
    writeAuditLog(
        savedAccount.getId(),
        "CREATED",
        BigDecimal.ZERO,
        savedAccount.getBalance(),
        savedAccount.getBalance(),
        command.performedBy(),
        "Account created with initial deposit");

    // PCI-DSS requires masking account identifiers in logs because log storage usually has wider access than the DB.
    log.info("Account created for user={} account={}", command.userId(), dataMaskingUtil.maskAccountNumber(savedAccount.getAccountNumber()));

    accountEventPublisher.publishAccountCreated(new AccountCreatedEvent(
        UUID.randomUUID(),
        savedAccount.getId(),
        savedAccount.getUserId(),
        savedAccount.getAccountNumber(),
        savedAccount.getAccountType(),
        savedAccount.getBalance(),
        savedAccount.getCurrency(),
        savedAccount.getStatus(),
        LocalDateTime.now()));

    return accountViewMapper.toAccountResponse(savedAccount);
  }

  @Transactional
  @Retryable(retryFor = ObjectOptimisticLockingFailureException.class, maxAttempts = 3, backoff = @Backoff(delay = 100, multiplier = 2))
  public AccountResponse debitAccount(DebitAccountCommand command) {
    Account account = accountRepository.findById(command.accountId())
        .orElseThrow(() -> new ResourceNotFoundException("Account", "id", command.accountId()));

    if (account.getStatus() != AccountStatus.ACTIVE) {
      throw new AccountFrozenException(account.getId());
    }
    if (account.getBalance().compareTo(command.amount()) < 0) {
      throw new InsufficientFundsException(account.getId(), command.amount(), account.getBalance());
    }

    BigDecimal previousBalance = account.getBalance();
    BigDecimal newBalance = scale(previousBalance.subtract(command.amount()));
    account.setBalance(newBalance);
    Account savedAccount = accountRepository.save(account);

    writeAuditLog(
        savedAccount.getId(),
        "DEBITED",
        previousBalance,
        newBalance,
        scale(command.amount()),
        command.performedBy(),
        defaultDescription(command.description(), "Account debited"));
    evictAccountCaches(savedAccount.getId());

    log.info("Debit processed for account={} amount={}", dataMaskingUtil.maskAccountNumber(savedAccount.getAccountNumber()), dataMaskingUtil.maskAmount(command.amount()));

    accountEventPublisher.publishAccountDebited(new AccountDebitedEvent(
        UUID.randomUUID(),
        command.transactionId(),
        savedAccount.getId(),
        scale(command.amount()),
        newBalance,
        LocalDateTime.now()));

    return accountViewMapper.toAccountResponse(savedAccount);
  }

  @Transactional
  @Retryable(retryFor = ObjectOptimisticLockingFailureException.class, maxAttempts = 3, backoff = @Backoff(delay = 100, multiplier = 2))
  public AccountResponse creditAccount(CreditAccountCommand command) {
    Account account = accountRepository.findById(command.accountId())
        .orElseThrow(() -> new ResourceNotFoundException("Account", "id", command.accountId()));

    if (account.getStatus() == AccountStatus.CLOSED) {
      throw new IllegalStateException("Closed account cannot be credited: " + account.getId());
    }

    BigDecimal previousBalance = account.getBalance();
    BigDecimal newBalance = scale(previousBalance.add(command.amount()));
    account.setBalance(newBalance);
    Account savedAccount = accountRepository.save(account);

    writeAuditLog(
        savedAccount.getId(),
        "CREDITED",
        previousBalance,
        newBalance,
        scale(command.amount()),
        command.performedBy(),
        defaultDescription(command.description(), "Account credited"));
    evictAccountCaches(savedAccount.getId());

    log.info("Credit processed for account={} amount={}", dataMaskingUtil.maskAccountNumber(savedAccount.getAccountNumber()), dataMaskingUtil.maskAmount(command.amount()));

    accountEventPublisher.publishAccountCredited(new AccountCreditedEvent(
        UUID.randomUUID(),
        command.transactionId(),
        savedAccount.getId(),
        scale(command.amount()),
        newBalance,
        LocalDateTime.now()));

    return accountViewMapper.toAccountResponse(savedAccount);
  }

  @Transactional
  public AccountResponse updateAccountStatus(UpdateAccountStatusCommand command) {
    Account account = accountRepository.findById(command.accountId())
        .orElseThrow(() -> new ResourceNotFoundException("Account", "id", command.accountId()));

    AccountStatus previousStatus = account.getStatus();
    account.setStatus(command.status());
    Account savedAccount = accountRepository.save(account);

    writeAuditLog(
        savedAccount.getId(),
        "STATUS_CHANGED",
        savedAccount.getBalance(),
        savedAccount.getBalance(),
        BigDecimal.ZERO,
        command.performedBy(),
        defaultDescription(command.description(), "Status changed from " + previousStatus + " to " + command.status()));
    evictAccountCaches(savedAccount.getId());

    log.info("Status updated for account={} from={} to={}", dataMaskingUtil.maskAccountNumber(savedAccount.getAccountNumber()), previousStatus, command.status());

    return accountViewMapper.toAccountResponse(savedAccount);
  }

  private void writeAuditLog(
      UUID accountId,
      String action,
      BigDecimal previousBalance,
      BigDecimal newBalance,
      BigDecimal amount,
      UUID performedBy,
      String description) {
    AccountAuditLog auditLog = new AccountAuditLog();
    auditLog.setId(UUID.randomUUID());
    auditLog.setAccountId(accountId);
    auditLog.setAction(action);
    auditLog.setPreviousBalance(scale(previousBalance));
    auditLog.setNewBalance(scale(newBalance));
    auditLog.setAmount(scale(amount));
    auditLog.setPerformedBy(performedBy);
    auditLog.setPerformedAt(LocalDateTime.now());
    auditLog.setDescription(description);
    accountAuditLogRepository.save(auditLog);
  }

  private void evictAccountCaches(UUID accountId) {
    if (cacheManager.getCache("accounts") != null) {
      cacheManager.getCache("accounts").evict(accountId.toString());
    }
    if (cacheManager.getCache("balances") != null) {
      cacheManager.getCache("balances").evict(accountId.toString());
    }
  }

  private BigDecimal scale(BigDecimal amount) {
    return amount == null ? null : amount.setScale(2, RoundingMode.HALF_EVEN);
  }

  private String normalizeCurrency(String currency) {
    return currency == null || currency.isBlank() ? "INR" : currency.trim().toUpperCase(Locale.ROOT);
  }

  private String defaultDescription(String description, String fallback) {
    return description == null || description.isBlank() ? fallback : description;
  }
}
