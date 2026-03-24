package com.bankflow.auth.entity;

/**
 * Fixed role catalog for authentication and authorization.
 *
 * <p>Plain English: these are the only allowed roles in the auth system.
 *
 * <p>Design decision: roles are enums instead of free-form strings so security rules stay type
 * safe and seed data stays predictable.
 *
 * <p>Bug prevented: string typos in role names silently break access checks and are hard to trace.
 *
 * <p>Interview question answered: "Why do you model roles as enums instead of arbitrary strings?"
 */
public enum RoleName {
  ROLE_USER,
  ROLE_ADMIN,
  ROLE_TELLER
}
