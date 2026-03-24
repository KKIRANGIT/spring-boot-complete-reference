package com.bankflow.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Refresh-token rotation request.
 *
 * <p>Plain English: the caller submits the current refresh token to obtain a new token pair.
 *
 * <p>Interview question answered: "How do you model a refresh-token request body?"
 */
public record RefreshTokenRequest(@NotBlank String refreshToken) {
}
