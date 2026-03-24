package com.bankflow.auth.dto;

import java.util.UUID;

/**
 * Registration success payload.
 *
 * <p>Plain English: this tells the caller which user record was created.
 *
 * <p>Interview question answered: "What do you return after a successful registration?"
 */
public record RegisterResponse(UUID userId, String username, String email) {
}
