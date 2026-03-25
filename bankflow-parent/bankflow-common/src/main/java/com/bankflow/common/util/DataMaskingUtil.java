package com.bankflow.common.util;

import java.math.BigDecimal;
import org.springframework.stereotype.Component;

/**
 * Masks sensitive identifiers before they are written to logs.
 *
 * <p>Plain English: bank amounts stay visible for auditability, but account numbers and email
 * addresses are masked so log storage does not become a secondary data leak.
 *
 * <p>Security issue prevented: logs are usually copied into ELK, CloudWatch, or SIEM platforms with
 * broader human access than the transactional database, so identifiers must not be written in full.
 *
 * <p>Interview question answered: "How do you keep observability useful in banking systems without
 * leaking regulated identifiers into logs?"
 */
@Component
public class DataMaskingUtil {

  public String maskAccountNumber(String accountNumber) {
    if (accountNumber == null || accountNumber.length() < 4) {
      return "****";
    }
    return accountNumber.substring(0, 3) + "*****" + accountNumber.substring(accountNumber.length() - 4);
  }

  public String maskEmail(String email) {
    if (email == null || !email.contains("@")) {
      return "***";
    }
    String[] parts = email.split("@", 2);
    if (parts[0].isBlank()) {
      return "***@" + parts[1];
    }
    return parts[0].charAt(0) + "***@" + parts[1];
  }

  public BigDecimal maskAmount(BigDecimal amount) {
    // Amounts stay unmasked so investigators and auditors can reconcile ledger movements precisely.
    return amount;
  }
}
