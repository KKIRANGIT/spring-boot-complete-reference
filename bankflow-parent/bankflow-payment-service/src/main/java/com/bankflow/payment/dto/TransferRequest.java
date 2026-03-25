package com.bankflow.payment.dto;

import com.bankflow.payment.validation.NotSameAccount;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Transfer initiation request payload.
 *
 * <p>Plain English: this DTO rejects impossible or risky inputs at the HTTP boundary before the
 * payment saga can start.
 */
@NotSameAccount
public record TransferRequest(
    @NotNull(message = "Source account is required") UUID fromAccountId,
    @NotNull(message = "Destination account is required") UUID toAccountId,
    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be at least 0.01")
    @DecimalMax(value = "100000.00", message = "Amount cannot exceed 100,000")
    BigDecimal amount,
    @NotBlank String currency,
    @Size(max = 255) String description) {
}
