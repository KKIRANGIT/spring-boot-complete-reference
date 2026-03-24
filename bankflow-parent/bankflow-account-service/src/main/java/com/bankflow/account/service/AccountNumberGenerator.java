package com.bankflow.account.service;

import com.bankflow.account.repository.AccountRepository;
import java.security.SecureRandom;
import org.springframework.stereotype.Component;

/**
 * Generates unique customer-facing account numbers.
 *
 * <p>Plain English: this creates numbers like {@code BNK123456789} and retries until the value is
 * unique in the account table.
 *
 * <p>Interview question answered: "How do you generate a readable account number without exposing
 * internal database ids?"
 */
@Component
public class AccountNumberGenerator {

  private static final String PREFIX = "BNK";
  private static final int DIGIT_BOUND = 1_000_000_000;

  private final SecureRandom secureRandom = new SecureRandom();
  private final AccountRepository accountRepository;

  public AccountNumberGenerator(AccountRepository accountRepository) {
    this.accountRepository = accountRepository;
  }

  /**
   * Generates a unique account number using the bank prefix plus nine digits.
   */
  public String generate() {
    String candidate;
    do {
      candidate = PREFIX + String.format("%09d", secureRandom.nextInt(DIGIT_BOUND));
    } while (accountRepository.existsByAccountNumber(candidate));
    return candidate;
  }
}
