package com.bankflow.payment.repository;

import com.bankflow.payment.entity.Transaction;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for durable payment transactions.
 */
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

  Optional<Transaction> findByIdempotencyKey(String idempotencyKey);

  Optional<Transaction> findByTransactionReference(String transactionReference);
}
