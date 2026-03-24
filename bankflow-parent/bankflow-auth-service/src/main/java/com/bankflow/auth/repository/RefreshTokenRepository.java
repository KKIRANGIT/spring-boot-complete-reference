package com.bankflow.auth.repository;

import com.bankflow.auth.entity.RefreshToken;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repository for refresh-token persistence and revocation.
 *
 * <p>Plain English: this interface finds refresh tokens and bulk-revokes them for a user when all
 * sessions must be terminated.
 *
 * <p>Interview question answered: "How do you implement logout-all-devices without scanning all
 * rows in application code?"
 */
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

  /**
   * Looks up a refresh token by its external token string.
   */
  Optional<RefreshToken> findByToken(String token);

  /**
   * Revokes all active refresh tokens for a given user in one SQL update.
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("update RefreshToken rt set rt.revoked = true where rt.user.id = :userId and rt.revoked = false")
  int revokeAllByUserId(@Param("userId") UUID userId);
}
