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

@Getter
@Setter
@NoArgsConstructor
@ToString(exclude = {"token", "user"})
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
