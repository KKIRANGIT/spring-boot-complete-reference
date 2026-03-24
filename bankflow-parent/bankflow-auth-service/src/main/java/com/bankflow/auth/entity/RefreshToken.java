package com.bankflow.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Persisted refresh token record.
 *
 * <p>Plain English: this table tracks long-lived refresh tokens so they can be rotated and revoked.
 *
 * <p>Design decision: refresh tokens live in MySQL because JWT access tokens are stateless and
 * cannot be individually revoked once issued.
 *
 * <p>Security issue prevented: storing refresh tokens allows session revocation after theft,
 * password reset, or "logout all devices" events.
 *
 * <p>Interview question answered: "Why do you store refresh tokens in a database if access tokens
 * are stateless JWTs?"
 */
@Getter
@Setter
@NoArgsConstructor
@ToString(exclude = "user")
@Entity
@Table(name = "refresh_tokens")
@EntityListeners(AuditingEntityListener.class)
public class RefreshToken {

  @Id
  private UUID id;

  @Column(nullable = false, unique = true, length = 64)
  private String token;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(nullable = false)
  private LocalDateTime expiresAt;

  @Column(nullable = false)
  private boolean revoked = false;

  @CreatedDate
  @Column(nullable = false, updatable = false)
  private LocalDateTime createdAt;
}
