package com.bankflow.payment.validation;

import com.bankflow.payment.dto.TransferRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Validator for {@link NotSameAccount}.
 *
 * <p>Security issue prevented: without this guard, self-transfers can enter the saga and create
 * misleading ledger history that looks like a real inter-account payment.
 */
public class SameAccountValidator implements ConstraintValidator<NotSameAccount, TransferRequest> {

  @Override
  public boolean isValid(TransferRequest request, ConstraintValidatorContext context) {
    if (request == null || request.fromAccountId() == null || request.toAccountId() == null) {
      return true;
    }

    boolean valid = !request.fromAccountId().equals(request.toAccountId());
    if (!valid) {
      context.disableDefaultConstraintViolation();
      context.buildConstraintViolationWithTemplate("Source and destination accounts must be different")
          .addPropertyNode("toAccountId")
          .addConstraintViolation();
    }
    return valid;
  }
}
