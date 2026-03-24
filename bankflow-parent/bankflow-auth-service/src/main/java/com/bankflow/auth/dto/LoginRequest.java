package com.bankflow.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Login payload for username/email plus password sign-in.
 *
 * <p>Plain English: the caller can sign in with either a username or an email address.
 *
 * <p>Interview question answered: "How do you support username-or-email login in one endpoint?"
 */
public record LoginRequest(
    @NotBlank String usernameOrEmail,
    @NotBlank String password) {
}
