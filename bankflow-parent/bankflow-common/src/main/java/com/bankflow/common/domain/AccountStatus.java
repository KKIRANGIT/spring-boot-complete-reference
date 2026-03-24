package com.bankflow.common.domain;

/**
 * Operational status of a bank account.
 *
 * <p>Design decision: the account lifecycle is constrained to a controlled enum so business logic
 * cannot invent unsupported string states at runtime.
 *
 * <p>Interview answer: this class shows how domain enums protect consistency in financial systems.
 *
 * <p>Bug prevented: free-form statuses such as "freeze", "Frozen", and "FROZEN" create subtle
 * branching errors and can accidentally bypass controls.
 */
public enum AccountStatus {
  ACTIVE,
  INACTIVE,
  FROZEN,
  CLOSED
}
