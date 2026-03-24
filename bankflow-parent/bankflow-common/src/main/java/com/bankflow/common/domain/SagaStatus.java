package com.bankflow.common.domain;

/**
 * State machine for distributed payment saga execution.
 *
 * <p>Design decision: long-running orchestration status is explicit because distributed banking
 * flows need to distinguish forward progress from compensation progress.
 *
 * <p>Interview answer: this class shows how to model saga orchestration in a production system.
 *
 * <p>Bug prevented: without separate compensation states, recovery logic can retry the wrong step
 * or miss a failed reversal.
 */
public enum SagaStatus {
  STARTED,
  ACCOUNT_DEBITED,
  COMPLETED,
  COMPENSATING,
  COMPENSATED,
  FAILED
}
