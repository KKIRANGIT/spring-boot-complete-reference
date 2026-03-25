package com.bankflow.account.security;

import com.bankflow.account.entity.Account;
import com.bankflow.account.repository.AccountRepository;
import com.bankflow.common.exception.ResourceNotFoundException;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * Method-security helper for account ownership checks.
 *
 * <p>Plain English: URL security answers "is this caller authenticated?" while this bean answers
 * "does this caller own the specific account being requested?"
 *
 * <p>Security issue prevented: authenticated access alone is not enough for banking APIs because a
 * user could enumerate another customer's account id unless object-level ownership is enforced.
 */
@Component("accountSecurityService")
public class AccountSecurityService {

  private final AccountRepository accountRepository;

  public AccountSecurityService(AccountRepository accountRepository) {
    this.accountRepository = accountRepository;
  }

  public boolean isOwner(Authentication authentication, UUID accountId) {
    if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails user)) {
      return false;
    }

    Account account = accountRepository.findById(accountId)
        .orElseThrow(() -> new ResourceNotFoundException("Account", "id", accountId));
    return account.getUserId().equals(user.getId());
  }
}
