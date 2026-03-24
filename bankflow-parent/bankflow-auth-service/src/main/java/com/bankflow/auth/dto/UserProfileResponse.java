package com.bankflow.auth.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Authenticated user profile payload.
 *
 * <p>Plain English: this is the lightweight user summary returned to the currently signed-in user.
 *
 * <p>Interview question answered: "What user information is safe and useful to return from a
 * profile endpoint?"
 */
public record UserProfileResponse(
    UUID id,
    String username,
    String email,
    List<String> roles,
    LocalDateTime createdAt,
    LocalDateTime lastLoginAt) {
}
