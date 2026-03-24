# BankFlow Architecture

BankFlow is organized as five Spring Boot services around clear banking domains. The platform combines synchronous HTTP calls for edge access with asynchronous Kafka events for cross-service workflows and eventual consistency.

## Core Services

### API Gateway

- Single public entry point for clients.
- Routes requests to internal services.
- Enforces cross-cutting concerns such as authentication, authorization, rate limiting, and request tracing.
- Exposes `/actuator/prometheus` for platform-level edge metrics.

### Auth Service

- Manages users, credentials, roles, OTP, token issuance, refresh tokens, and security policies.
- Persists data in the `bankflow_auth` schema.
- Publishes user lifecycle and security events for downstream consumers.
- Uses Redis for OTPs, session state, token revocation, and short-lived security data.

### Account Service

- Owns customer accounts, balances, account status, and ledger-related metadata.
- Persists data in the `bankflow_account` schema.
- Publishes account domain events that other services can consume without tight coupling.
- May use Redis for read caching and account lock state.

### Payment Service

- Owns payment initiation, transfer processing, transaction state, idempotency, and orchestration.
- Persists data in the `bankflow_payment` schema.
- Runs the main business workflow for money movement.
- Publishes state transitions to Kafka so downstream services react independently.

### Notification Service

- Consumes business events and turns them into customer-facing notifications.
- Persists templates, delivery attempts, and notification history in `bankflow_notification`.
- Sends email through MailHog in local development.
- Supports outbox-style delivery and retry semantics.

## Shared Infrastructure

- MySQL: shared database engine with a separate schema per domain service.
- Redis: cache, OTP store, token blacklist, idempotency, and transient workflow state.
- Kafka: asynchronous event bus between services.
- MailHog: local SMTP sink for emails.
- Prometheus: metrics scraping for all services.
- Grafana: operational dashboards.
- SonarQube + PostgreSQL: code quality analysis stack.

## Communication Model

### Synchronous HTTP

- Clients call the API Gateway.
- Gateway forwards to Auth, Account, Payment, or Notification APIs as needed.
- Synchronous calls are best for request/response use cases that need immediate answers.

### Asynchronous Events

- Services publish domain events to Kafka.
- Other services consume only what they need.
- This reduces temporal coupling and keeps workflows resilient.
- Notification delivery, audit processing, and payment follow-up are good event-driven candidates.

## Service Interaction Summary

```text
Client
  |
  v
API Gateway
  |------> Auth Service ---------> MySQL(bankflow_auth)
  |             |                        |
  |             +-------> Redis <--------+
  |
  |------> Account Service ------> MySQL(bankflow_account)
  |
  |------> Payment Service ------> MySQL(bankflow_payment)
  |             |
  |             +-------> Kafka topics ------+
  |                                          |
  +------> Notification Service <------------+
                |
                +-------> MySQL(bankflow_notification)
                |
                +-------> MailHog

Metrics: all services -> Prometheus -> Grafana
Quality: source code -> SonarQube -> PostgreSQL
```

## Kafka Topics

The exact topic set can evolve, but this is the recommended starting contract:

- `bankflow.auth.user-created`
  Used when a new user is registered successfully.
- `bankflow.auth.password-reset-requested`
  Used to trigger notification workflows for password reset mail or OTP delivery.
- `bankflow.account.account-created`
  Published after a customer account is opened.
- `bankflow.account.balance-updated`
  Published whenever a balance-affecting operation completes.
- `bankflow.payment.initiated`
  Marks the start of a payment workflow.
- `bankflow.payment.validated`
  Indicates business validation passed.
- `bankflow.payment.completed`
  Indicates the payment finished successfully.
- `bankflow.payment.failed`
  Indicates the payment failed and may require compensation or customer notification.
- `bankflow.notification.email-requested`
  Internal trigger topic for email delivery.
- `bankflow.audit.activity`
  Optional audit stream for security-sensitive or money-sensitive actions.

## Payment Saga Flow

The payment lifecycle is a natural Saga because it spans validation, account effects, notifications, and failure handling.

```text
1. Client -> API Gateway -> Payment Service : create payment
2. Payment Service -> MySQL(bankflow_payment) : save PENDING payment
3. Payment Service -> Kafka : publish bankflow.payment.initiated
4. Payment Service -> Account Service : validate source/target account state
5. Account Service -> MySQL(bankflow_account) : verify accounts and balances
6. Account Service -> Payment Service : validation result
7. Payment Service -> MySQL(bankflow_payment) : mark VALIDATED or FAILED
8. Payment Service -> Kafka : publish bankflow.payment.completed or bankflow.payment.failed
9. Notification Service <- Kafka : consume result event
10. Notification Service -> MailHog : send confirmation/failure email
11. Notification Service -> MySQL(bankflow_notification) : persist delivery result
```

## Failure Handling Notes

- If account validation fails, Payment Service marks the payment as failed and emits `bankflow.payment.failed`.
- If notification delivery fails, the payment remains complete; Notification Service retries independently.
- Redis can hold idempotency keys to prevent duplicate payment submissions.
- Observability is mandatory for distributed failures, so each service should expose health, metrics, and structured logs.

## Design Principles

- Database per service schema for bounded ownership.
- API Gateway at the edge, not in the middle of internal service-to-service complexity.
- Kafka for integration events, not for replacing every HTTP interaction.
- Strong observability from the start.
- Local development should feel production-like without being operationally heavy.
