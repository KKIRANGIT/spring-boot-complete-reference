package com.bankflow.common.exception;

/**
 * Raised when a requested resource does not exist.
 *
 * <p>Design decision: a dedicated 404 exception is better than reusing IllegalArgumentException,
 * because the global handler can map it to the correct HTTP semantics every time.
 *
 * <p>Interview answer: this class shows how business exceptions protect contract correctness in a
 * microservice API.
 *
 * <p>Bug prevented: without a specific exception type, a missing row can be reported as a 500 and
 * look like a server failure instead of a client-visible not-found case.
 */
public class ResourceNotFoundException extends RuntimeException {

  public ResourceNotFoundException(String resourceName, String field, Object value) {
    super(resourceName + " not found with " + field + ": " + value);
  }
}
