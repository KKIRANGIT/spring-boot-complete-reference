package com.bankflow.account.service;

import com.bankflow.account.dto.AccountBalanceResponse;
import com.bankflow.account.dto.AccountResponse;
import com.bankflow.account.dto.AccountStatementEntryResponse;
import com.bankflow.account.entity.Account;
import com.bankflow.account.entity.AccountAuditLog;
import java.time.LocalDateTime;
import org.springframework.stereotype.Component;

/**
 * Maps write-model entities into query-model DTOs.
 *
 * <p>Plain English: this keeps HTTP payload shaping out of the command and query services.
 *
 * <p>Bug prevented: leaking JPA entities directly to controllers couples API responses to database
 * internals and makes cache serialization brittle.
 */
@Component
public class AccountViewMapper {

  /**
   * Converts an account aggregate into the standard account response payload.
   */
  public AccountResponse toAccountResponse(Account account) {
    return new AccountResponse(
        account.getId(),
        account.getAccountNumber(),
        account.getUserId(),
        account.getAccountType(),
        account.getBalance(),
        account.getCurrency(),
        account.getStatus(),
        account.getCreatedAt(),
        account.getUpdatedAt());
  }

  /**
   * Converts an account aggregate into the lightweight balance response payload.
   */
  public AccountBalanceResponse toBalanceResponse(Account account) {
    return new AccountBalanceResponse(
        account.getId(),
        account.getBalance(),
        account.getCurrency(),
        account.getStatus(),
        LocalDateTime.now());
  }

  /**
   * Converts an audit row into a statement line for paged history APIs.
   */
  public AccountStatementEntryResponse toStatementEntry(AccountAuditLog auditLog) {
    return new AccountStatementEntryResponse(
        auditLog.getId(),
        auditLog.getAction(),
        auditLog.getPreviousBalance(),
        auditLog.getNewBalance(),
        auditLog.getAmount(),
        auditLog.getPerformedBy(),
        auditLog.getPerformedAt(),
        auditLog.getDescription());
  }
}
