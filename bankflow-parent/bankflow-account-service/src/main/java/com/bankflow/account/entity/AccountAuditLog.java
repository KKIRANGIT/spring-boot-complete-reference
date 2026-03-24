package com.bankflow.account.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Immutable-style audit record for account balance and status mutations.
 *
 * <p>Plain English: every financial or operational change writes one row here so auditors can see
 * who changed what, when it happened, and how the balance moved.
 *
 * <p>Design decision: this is an append-only history table rather than something updated in place.
 * Regulators care about history, not just the final balance snapshot.
 *
 * <p>Interview question answered: "How do you make account mutations legally auditable?"
 */
@Getter
@Setter
@NoArgsConstructor
@ToString
@Entity
@Table(name = "account_audit_logs")
public class AccountAuditLog {

  @Id
  private UUID id;

  @Column(nullable = false)
  private UUID accountId;

  @Column(nullable = false, length = 40)
  private String action;

  @Column(precision = 19, scale = 2)
  private BigDecimal previousBalance;

  @Column(precision = 19, scale = 2)
  private BigDecimal newBalance;

  @Column(precision = 19, scale = 2)
  private BigDecimal amount;

  private UUID performedBy;

  @Column(nullable = false)
  private LocalDateTime performedAt;

  @Column(length = 255)
  private String description;
}
