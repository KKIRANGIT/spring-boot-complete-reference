package com.bankflow.account.dto;

import com.bankflow.common.domain.AccountType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;

/**
 * Request payload for creating a new account.
 *
 * <p>Plain English: the caller chooses the product type and initial deposit, while account-service
 * generates the bank account number itself.
 */
public record CreateAccountRequest(
    @NotNull AccountType accountType,
    @NotNull @DecimalMin(value = "0.00") BigDecimal initialDeposit,
    @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be a 3-letter ISO code") String currency) {
}
