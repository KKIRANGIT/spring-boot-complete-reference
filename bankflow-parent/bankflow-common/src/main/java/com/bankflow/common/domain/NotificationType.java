package com.bankflow.common.domain;

/**
 * Supported notification channels.
 *
 * <p>Design decision: the channel catalog is shared so producers and consumers agree on how a
 * notification should be delivered.
 *
 * <p>Interview answer: this class shows how to share event payload semantics between services.
 *
 * <p>Bug prevented: channel mismatches between services can silently drop user notifications or
 * route them to the wrong delivery adapter.
 */
public enum NotificationType {
  EMAIL,
  SMS,
  PUSH
}
