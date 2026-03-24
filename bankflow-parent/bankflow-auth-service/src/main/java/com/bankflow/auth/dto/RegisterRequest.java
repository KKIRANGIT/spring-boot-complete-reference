package com.bankflow.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Registration payload for creating a new auth user.
 *
 * <p>Plain English: this record captures the username, email, and strong password that a new user
 * must submit.
 *
 * <p>Design decision: password complexity is validated at the API boundary so weak credentials are
 * rejected before any database or hashing work is done.
 *
 * <p>Security issue prevented: accepting weak passwords makes brute-force attacks much cheaper.
 *
 * <p>Interview question answered: "Where should password validation happen in a REST API?"
 */
public record RegisterRequest(
    @NotBlank @Size(min = 3, max = 50) String username,
    @NotBlank @Email String email,
    @NotBlank
    @Pattern(
        regexp = "^(?=.*[A-Z])(?=.*[0-9])(?=.*[!@#$%]).{8,}$",
        message = "Min 8 chars, 1 uppercase, 1 digit, 1 special char")
    String password) {
}
