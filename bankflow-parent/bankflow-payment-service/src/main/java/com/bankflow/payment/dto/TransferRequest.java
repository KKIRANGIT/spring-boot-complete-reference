package com.bankflow.payment.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Transfer initiation request payload.
 */
public record TransferRequest(
    @NotNull UUID fromAccountId,
    @NotNull UUID toAccountId,
    @NotNull @DecimalMin(value = "0.01", inclusive = true) BigDecimal amount,
    @NotBlank String currency,
    String description) {
}
