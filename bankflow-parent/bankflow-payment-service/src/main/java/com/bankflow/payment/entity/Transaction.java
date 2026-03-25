package com.bankflow.payment.entity;

import com.bankflow.common.domain.SagaStatus;
import com.bankflow.common.domain.TransactionStatus;
import com.bankflow.common.domain.TransactionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Getter
@Setter
@NoArgsConstructor
@ToString
@Entity
@Table(name = "transactions")
@EntityListeners(AuditingEntityListener.class)
public class Transaction {

  @Id
  private UUID id;

  @Column(nullable = false, unique = true, length = 30)
  private String transactionReference;

  @Column(nullable = false, unique = true, length = 120)
  private String idempotencyKey;

  @Column
  private UUID initiatedByUserId;

  @Column(nullable = false)
  private UUID fromAccountId;

  @Column(nullable = false)
  private UUID toAccountId;

  @Column(nullable = false, precision = 19, scale = 2)
  private BigDecimal amount;

  @Column(nullable = false, length = 3)
  private String currency = "INR";

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private TransactionType type = TransactionType.TRANSFER;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private TransactionStatus status = TransactionStatus.PENDING;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private SagaStatus sagaStatus = SagaStatus.STARTED;

  @Column(length = 255)
  private String description;

  @Column(length = 255)
  private String failureReason;

  @CreatedDate
  @Column(nullable = false, updatable = false)
  private LocalDateTime createdAt;

  private LocalDateTime completedAt;
}
