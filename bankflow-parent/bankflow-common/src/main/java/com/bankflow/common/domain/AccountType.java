package com.bankflow.common.domain;

/**
 * Supported account types in BankFlow.
 *
 * <p>Design decision: account type choices are fixed in code so downstream rules like fees,
 * interest, and transfer policies can branch safely.
 *
 * <p>Interview answer: this class shows how to encode product taxonomy in a maintainable way.
 *
 * <p>Bug prevented: string values for financial product types are easy to mistype and hard to
 * refactor safely across services.
 */
public enum AccountType {
  SAVINGS,
  CURRENT,
  FIXED_DEPOSIT
}
