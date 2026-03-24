package com.bankflow.auth.dto;

import java.util.List;
import java.util.UUID;

/**
 * Token issuance payload returned by login and refresh flows.
 *
 * <p>Plain English: this contains the short-lived access token, the rotating refresh token, and
 * the caller identity metadata the client usually needs immediately after authentication.
 *
 * <p>Interview question answered: "What should an auth service return after a successful login?"
 */
public record LoginResponse(
    String accessToken,
    String refreshToken,
    String tokenType,
    long expiresIn,
    UUID userId,
    String username,
    List<String> roles) {
}
