package com.bankflow.payment.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Class-level constraint that prevents same-account transfers.
 *
 * <p>Plain English: validation fails before the service layer if the source and destination account
 * ids are equal.
 */
@Documented
@Constraint(validatedBy = SameAccountValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface NotSameAccount {

  String message() default "Source and destination accounts must be different";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
