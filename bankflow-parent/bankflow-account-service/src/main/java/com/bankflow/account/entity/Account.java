package com.bankflow.account.entity;

import com.bankflow.common.domain.AccountStatus;
import com.bankflow.common.domain.AccountType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Write-model aggregate for a customer's bank account.
 *
 * <p>Plain English: this entity stores the current balance and lifecycle state that account-service
 * is allowed to mutate.
 *
 * <p>Design decision: {@link BigDecimal} is mandatory for money because IEEE 754 floating point
 * cannot represent decimal currency exactly. In interviews, the classic example is
 * {@code 0.1 + 0.2 = 0.30000000000000004}; that tiny drift is unacceptable in a banking ledger.
 * {@code new BigDecimal("0.1").add(new BigDecimal("0.2"))} stays exactly {@code 0.3} every time.
 *
 * <p>Design decision: {@link Version} protects concurrent balance updates. Without optimistic
 * locking, two debit threads can both read balance {@code 1000}, write conflicting values, and
 * silently lose money. With {@code @Version}, stale writes fail and the retry path re-checks the
 * latest balance before approving the debit.
 *
 * <p>Interview question answered: "How do you model a bank account safely for money precision and
 * concurrent debits in a microservice?"
 */
@Getter
@Setter
@NoArgsConstructor
@ToString
@Entity
@Table(name = "accounts")
@EntityListeners(AuditingEntityListener.class)
public class Account {

  @Id
  private UUID id;

  @Column(nullable = false, unique = true, length = 12)
  private String accountNumber;

  @Column(nullable = false)
  private UUID userId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private AccountType accountType;

  @Column(nullable = false, precision = 19, scale = 2)
  private BigDecimal balance = BigDecimal.ZERO;

  @Column(nullable = false, length = 3)
  private String currency = "INR";

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private AccountStatus status = AccountStatus.ACTIVE;

  @CreatedDate
  @Column(nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @LastModifiedDate
  @Column(nullable = false)
  private LocalDateTime updatedAt;

  @Version
  private Long version;
}
