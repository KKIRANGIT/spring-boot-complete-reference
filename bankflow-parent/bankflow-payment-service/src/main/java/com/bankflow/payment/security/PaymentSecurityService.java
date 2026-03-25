package com.bankflow.payment.security;

import com.bankflow.common.exception.ResourceNotFoundException;
import com.bankflow.payment.entity.Transaction;
import com.bankflow.payment.repository.TransactionRepository;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * Method-security helper for payment ownership checks.
 *
 * <p>Plain English: this authorizes access to a payment only for the user who initiated it or an
 * administrator approved by method security.
 */
@Component("paymentSecurityService")
public class PaymentSecurityService {

  private final TransactionRepository transactionRepository;

  public PaymentSecurityService(TransactionRepository transactionRepository) {
    this.transactionRepository = transactionRepository;
  }

  public boolean isParticipant(Authentication authentication, UUID transactionId) {
    if (authentication == null
        || transactionId == null
        || !(authentication.getPrincipal() instanceof CustomUserDetails user)) {
      return false;
    }

    Transaction transaction = transactionRepository.findById(transactionId)
        .orElseThrow(() -> new ResourceNotFoundException("Transaction", "id", transactionId));
    return user.getId().equals(transaction.getInitiatedByUserId());
  }

  public boolean isParticipantByReference(Authentication authentication, String transactionReference) {
    if (authentication == null
        || transactionReference == null
        || transactionReference.isBlank()
        || !(authentication.getPrincipal() instanceof CustomUserDetails user)) {
      return false;
    }

    Transaction transaction = transactionRepository.findByTransactionReference(transactionReference)
        .orElseThrow(() -> new ResourceNotFoundException("Transaction", "transactionReference", transactionReference));
    return user.getId().equals(transaction.getInitiatedByUserId());
  }
}
