# BankFlow Project Reference Guide

This is the "understand the whole project" guide for BankFlow.

It is written for someone who is new to distributed systems, but it does not talk down to you. The goal is simple:

- explain what each part does,
- explain why it exists,
- show where it lives in the code,
- explain when it is used,
- explain how the pieces talk to each other,
- explain what problem it prevents,
- explain what alternatives exist,
- and explain why this design is a strong fit for BankFlow.

This is not a textbook. Read it like a guided walkthrough from one engineer to another.

## 1. Start With The Big Picture

BankFlow is a distributed banking backend split into focused services instead of one large monolith.

The core idea is:

- `api-gateway` is the only public entry point.
- `auth-service` owns identity and tokens.
- `account-service` owns accounts and balances.
- `payment-service` owns transfer workflow and transaction state.
- `notification-service` owns customer-facing notifications.
- `bankflow-common` holds shared contracts, enums, errors, event classes, constants, and utilities.

You can see the module list in [pom.xml](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/pom.xml).

Why split it this way?

- Banking domains naturally have different responsibilities.
- Account balance logic should not be mixed with login logic.
- Payment workflows are more complex and failure-prone than simple CRUD.
- Notifications should not slow down the payment path.
- The gateway is the correct place for internet-facing protection and routing.

Could this have been one monolith?

Yes. For a small team or small business flow, a modular monolith is often the best first choice.

Why is this microservice design still valid here?

- BankFlow is intentionally built as a distributed systems learning and portfolio project.
- The patterns you want to demonstrate, like Saga, Outbox, idempotency, gateway security, and Kafka consumers, make sense only when the system is actually distributed.

So the honest answer is:

This is not "perfect for every company." It is a strong and deliberate fit for a banking-style distributed architecture where correctness, isolation, and resilience matter.

## 2. How To Read The Repository

There are three layers in this repo:

### Infrastructure Layer

- [docker-compose.infrastructure.yml](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/docker-compose.infrastructure.yml)
- [config](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/config)

This layer gives you MySQL, Redis, Kafka, MailHog, Prometheus, Grafana, SonarQube, and PostgreSQL for SonarQube.

### Application Layer

- [bankflow-parent](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent)

This is the actual Java code. The parent Maven module aggregates all services.

### Developer Experience Layer

- [README.md](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/README.md)
- [docs/local-setup.md](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/docs/local-setup.md)
- [postman](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/postman)
- [start-infra.bat](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/start-infra.bat)
- [start-infra.sh](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/start-infra.sh)

This layer makes the system runnable and testable locally.

## 3. The Core Runtime Story

Here is the simplest way to understand BankFlow:

1. A client sends a request to the gateway.
2. The gateway checks the token and rate limits the request.
3. The gateway routes the request to the correct internal service.
4. The service does its local job using MySQL, Redis, or both.
5. If the job affects other services, it emits Kafka events.
6. Other services react asynchronously.
7. Metrics, logs, and health endpoints expose what is happening.

That model alone will help you understand most of the codebase.

## 4. API Gateway: Why It Exists First

Main files:

- [BankflowApiGatewayApplication.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-api-gateway/src/main/java/com/bankflow/gateway/BankflowApiGatewayApplication.java)
- [application.yml](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-api-gateway/src/main/resources/application.yml)
- [JwtAuthenticationFilter.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-api-gateway/src/main/java/com/bankflow/gateway/filter/JwtAuthenticationFilter.java)
- [GatewayJwtService.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-api-gateway/src/main/java/com/bankflow/gateway/service/GatewayJwtService.java)
- [GatewayConfig.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-api-gateway/src/main/java/com/bankflow/gateway/config/GatewayConfig.java)
- [GatewaySecurityConfig.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-api-gateway/src/main/java/com/bankflow/gateway/config/GatewaySecurityConfig.java)
- [SecurityHeadersFilter.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-api-gateway/src/main/java/com/bankflow/gateway/filter/SecurityHeadersFilter.java)
- [CorrelationIdFilter.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-api-gateway/src/main/java/com/bankflow/gateway/filter/CorrelationIdFilter.java)
- [FallbackController.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-api-gateway/src/main/java/com/bankflow/gateway/controller/FallbackController.java)

### What it does

The gateway is the front door.

It handles:

- routing,
- JWT validation,
- token blacklist checks,
- rate limiting,
- circuit breakers,
- correlation IDs,
- and security headers.

### Why it exists

Without a gateway, every service would need to:

- validate JWTs,
- implement its own rate limiter,
- handle internet-facing abuse,
- and duplicate routing logic.

That creates drift and inconsistency fast.

### How it works

`JwtAuthenticationFilter` checks whether the path is public. If it is not public, it validates the token, checks if it is blacklisted, extracts `userId` and roles, and forwards those as trusted internal headers like `X-User-Id` and `X-User-Roles`.

That means downstream services do not validate JWTs again. They trust the gateway.

This is a standard internal-trust pattern in microservices.

### Why this is a good fit here

For BankFlow, this is the right place to put edge security because:

- only one component faces the internet,
- policies stay consistent,
- and downstream services stay simpler.

### Alternative

Alternative: every service validates JWT itself.

Why not do that here?

- more duplicated code,
- more chances for drift,
- more security policy inconsistency,
- and more work every time auth rules change.

### Important honesty

This trust model is good inside a controlled network. In a production zero-trust network, you would often add mTLS or service identity so headers cannot be spoofed by an internal attacker.

## 5. Auth Service: Identity, Login, and Token Lifecycle

Main files:

- [AuthController.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-auth-service/src/main/java/com/bankflow/auth/controller/AuthController.java)
- [AuthService.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-auth-service/src/main/java/com/bankflow/auth/service/AuthService.java)
- [JwtService.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-auth-service/src/main/java/com/bankflow/auth/service/JwtService.java)
- [User.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-auth-service/src/main/java/com/bankflow/auth/entity/User.java)
- [RefreshToken.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-auth-service/src/main/java/com/bankflow/auth/entity/RefreshToken.java)
- [Role.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-auth-service/src/main/java/com/bankflow/auth/entity/Role.java)
- [SecurityConfig.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-auth-service/src/main/java/com/bankflow/auth/config/SecurityConfig.java)

### What it does

Auth service owns:

- user registration,
- login,
- access token issuance,
- refresh token rotation,
- logout,
- logout-all-devices,
- account lockout after failed logins,
- and current user profile retrieval.

### Why it is separate

Authentication is a different problem from account balance management.

If auth and balance were in the same service:

- security code and money code get mixed,
- changes become risky,
- and scaling identity traffic becomes harder.

Keeping auth separate is one of the cleanest boundaries in the whole platform.

### How login works in BankFlow

`AuthService.login(...)`:

1. finds the user by username or email,
2. checks whether the user is active,
3. checks whether the account is locked,
4. auto-unlocks if the lock window already expired,
5. compares the BCrypt password hash,
6. increments failed attempts on bad password,
7. locks the account after repeated failures,
8. resets counters on success,
9. creates JWT access token,
10. creates DB-backed refresh token,
11. returns both to the client.

### Why refresh tokens are in the DB

Access tokens are stateless JWTs. Once issued, they cannot be individually revoked unless you add blacklist state.

Refresh tokens are therefore stored in MySQL so BankFlow can:

- revoke them,
- rotate them,
- expire them,
- and kill all sessions if needed.

That is why [RefreshToken.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-auth-service/src/main/java/com/bankflow/auth/entity/RefreshToken.java) matters.

### Why JWT includes `jti`

`jti` is the token ID. BankFlow uses it in [JwtService.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-auth-service/src/main/java/com/bankflow/auth/service/JwtService.java) to blacklist one specific access token in Redis.

Without `jti`, you cannot blacklist one token cleanly.

### Why this design is strong

This auth design is strong because it mixes:

- stateless fast access tokens,
- revocable refresh tokens,
- Redis blacklist for early logout,
- and rotation to detect token theft.

That combination is more realistic than a toy "just issue JWT and forget about it" auth setup.

### Alternatives

Alternative: session-based auth with server-side sessions only.

Why not here?

- it is less aligned with gateway + microservice edge design,
- and it does not demonstrate modern token lifecycle patterns.

Alternative: opaque access tokens with introspection.

Why not here?

- stronger central control,
- but slower and more infrastructure-heavy for this local-first project.

## 6. Account Service: The Source of Truth for Balances

Main files:

- [AccountController.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-account-service/src/main/java/com/bankflow/account/controller/AccountController.java)
- [Account.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-account-service/src/main/java/com/bankflow/account/entity/Account.java)
- [AccountAuditLog.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-account-service/src/main/java/com/bankflow/account/entity/AccountAuditLog.java)
- [AccountCommandService.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-account-service/src/main/java/com/bankflow/account/service/AccountCommandService.java)
- [AccountQueryService.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-account-service/src/main/java/com/bankflow/account/service/AccountQueryService.java)
- [AccountSagaConsumer.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-account-service/src/main/java/com/bankflow/account/messaging/AccountSagaConsumer.java)
- [CacheConfig.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-account-service/src/main/java/com/bankflow/account/config/CacheConfig.java)
- [AccountSecurityService.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-account-service/src/main/java/com/bankflow/account/security/AccountSecurityService.java)

### What it does

Account service owns:

- account creation,
- account number generation,
- balance debit,
- balance credit,
- balance read,
- account status changes,
- and immutable-style audit records of balance change.

### Why it must be separate

This service is where money state lives.

That means:

- it must be conservative,
- it must be concurrency-safe,
- it must maintain an audit trail,
- and it must not let other services update balances directly.

That is why payment-service does not edit account tables. It asks account-service to do it through events.

### Why `BigDecimal` matters

The explanation in [Account.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-account-service/src/main/java/com/bankflow/account/entity/Account.java) is important:

- `double` is wrong for money,
- floating point introduces precision drift,
- and banking systems must preserve exact decimal correctness.

If you only remember one Java-money rule, remember this:

Never use floating point for money.

### Why `@Version` matters

`@Version` on [Account.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-account-service/src/main/java/com/bankflow/account/entity/Account.java) stops stale concurrent writes.

This is one of the most important correctness protections in the whole project.

### Why CQRS exists here

The account domain splits naturally into:

- command side: write logic in [AccountCommandService.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-account-service/src/main/java/com/bankflow/account/service/AccountCommandService.java)
- query side: read logic in [AccountQueryService.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-account-service/src/main/java/com/bankflow/account/service/AccountQueryService.java)

Why?

- writes must be fresh and authoritative,
- reads can be cached.

That separation is not just "clean code." It protects correctness.

### Why audit logs exist

[AccountAuditLog.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-account-service/src/main/java/com/bankflow/account/entity/AccountAuditLog.java) exists because every balance change in banking should be traceable.

This is not an optional nice-to-have.

If an auditor asks:

- who changed the balance,
- when it happened,
- by how much,
- and why,

the service must be able to answer.

### Why this design is strong

This is a strong banking service design because it combines:

- exact money arithmetic,
- optimistic locking,
- audit logs,
- CQRS,
- cache invalidation,
- and ownership-based security.

### Alternatives

Alternative: let payment-service update account balances directly.

Why not?

- it breaks ownership,
- couples payment to account persistence,
- and weakens domain boundaries.

Alternative: use pessimistic DB locks.

Why not here?

- stronger blocking,
- lower concurrency,
- and less elegant for a retry-based local banking demo.

## 7. Payment Service: The Heart of the Distributed Workflow

Main files:

- [PaymentController.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-payment-service/src/main/java/com/bankflow/payment/controller/PaymentController.java)
- [PaymentService.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-payment-service/src/main/java/com/bankflow/payment/service/PaymentService.java)
- [PaymentTransactionWriter.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-payment-service/src/main/java/com/bankflow/payment/service/PaymentTransactionWriter.java)
- [PaymentSagaService.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-payment-service/src/main/java/com/bankflow/payment/service/PaymentSagaService.java)
- [PaymentSagaConsumer.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-payment-service/src/main/java/com/bankflow/payment/messaging/PaymentSagaConsumer.java)
- [Transaction.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-payment-service/src/main/java/com/bankflow/payment/entity/Transaction.java)
- [OutboxEvent.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-payment-service/src/main/java/com/bankflow/payment/entity/OutboxEvent.java)
- [OutboxPublisher.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-payment-service/src/main/java/com/bankflow/payment/service/OutboxPublisher.java)

### What it does

Payment service owns:

- transfer initiation,
- idempotency,
- durable payment transaction state,
- saga state,
- outbox rows,
- and response to account-service events.

### Why it is the central workflow service

Payment is where the business transaction begins.

It is the right place to own:

- transaction reference,
- request validation,
- transfer lifecycle,
- failure reason,
- and compensation state.

### Why Saga is needed

A transfer touches multiple services:

- payment-service,
- account-service,
- and notification-service.

One local DB transaction cannot cover all of them.

That is why BankFlow uses saga choreography.

### Why the outbox exists here

Payment-service creates the most important cross-service events in the platform.

That means it is the service most exposed to the dual write problem.

So this is exactly where the Outbox pattern belongs.

### Why idempotency exists here

Payments are the worst place to accept duplicate retries.

If a user retries because the network timed out, the correct behavior is:

- same business outcome,
- same response shape,
- no second transfer.

That is what `PaymentService.initiateTransfer(...)` does.

### Why this design is strong

This service is strong because it does not assume the world is reliable.

It is built around the fact that:

- retries happen,
- Kafka can redeliver,
- services can crash,
- credit can fail after debit succeeds,
- and users still expect money safety.

### Alternatives

Alternative: synchronous payment workflow over REST only.

Why not here?

- tighter coupling,
- less resilience,
- and hard failure if one service is temporarily unavailable.

Alternative: orchestration engine instead of choreography.

Why not here?

- could be valid,
- but more moving parts,
- and choreography keeps the portfolio architecture more event-driven and service-owned.

## 8. Notification Service: Async Side Effects Done Safely

Main files:

- [NotificationKafkaConsumer.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-notification-service/src/main/java/com/bankflow/notification/messaging/NotificationKafkaConsumer.java)
- [KafkaConsumerConfig.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-notification-service/src/main/java/com/bankflow/notification/config/KafkaConsumerConfig.java)
- [NotificationIdempotencyService.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-notification-service/src/main/java/com/bankflow/notification/service/NotificationIdempotencyService.java)
- [NotificationLogService.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-notification-service/src/main/java/com/bankflow/notification/service/NotificationLogService.java)
- [EmailService.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-notification-service/src/main/java/com/bankflow/notification/service/EmailService.java)

### What it does

Notification service listens to Kafka events and sends customer-facing notifications.

Right now the local focus is email via MailHog.

### Why it is separate

Notifications are side effects.

They should not block:

- login success,
- account creation,
- or payment completion.

If email infrastructure is slow or broken, the main financial transaction should still complete.

### Why manual ack is important

In [KafkaConsumerConfig.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-notification-service/src/main/java/com/bankflow/notification/config/KafkaConsumerConfig.java), manual acknowledgment means the message is treated as consumed only after processing succeeds.

That is exactly what you want for reliability.

### Why DLT exists

Some messages will fail repeatedly.

When that happens, you do not want:

- infinite retries,
- blocked partitions,
- or hidden failure.

So BankFlow sends the message to a dead-letter topic after retries are exhausted.

That is mature behavior, even in local architecture.

### Why this design is strong

This service demonstrates an important engineering maturity point:

the main business transaction and the notification side effect are deliberately decoupled.

That is the correct default unless the side effect is legally part of the main commit.

## 9. bankflow-common: Shared Contracts Without Shared Databases

Main areas:

- shared error handling,
- shared enums,
- shared event payloads,
- shared constants,
- shared response wrapper,
- shared masking utility.

Useful files:

- [ApiResponse.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-common/src/main/java/com/bankflow/common/api/ApiResponse.java)
- [ErrorCode.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-common/src/main/java/com/bankflow/common/error/ErrorCode.java)
- [GlobalExceptionHandler.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-common/src/main/java/com/bankflow/common/handler/GlobalExceptionHandler.java)
- [KafkaTopics.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-common/src/main/java/com/bankflow/common/kafka/KafkaTopics.java)
- [CacheKeys.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-common/src/main/java/com/bankflow/common/cache/CacheKeys.java)
- [DataMaskingUtil.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-common/src/main/java/com/bankflow/common/util/DataMaskingUtil.java)

### Why it exists

Every microservice architecture hits this question:

what can be shared safely?

BankFlow shares:

- contracts,
- not persistence.

That is the correct line.

You do not want a shared business database.  
You do want shared event classes, error codes, and utility contracts.

## 10. Security Model Across The Whole Platform

The security story is layered.

### Gateway layer

Handled in gateway:

- JWT validation,
- revocation check,
- rate limiting,
- circuit breaking,
- response security headers,
- correlation ID propagation.

### Service layer

Handled inside services:

- method-level authorization using `@PreAuthorize`,
- ownership checks like [AccountSecurityService.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-account-service/src/main/java/com/bankflow/account/security/AccountSecurityService.java),
- participant checks like [PaymentSecurityService.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-payment-service/src/main/java/com/bankflow/payment/security/PaymentSecurityService.java),
- input validation,
- log masking,
- no sensitive `toString()` exposure,
- and no stack traces in client responses.

### Why layered security matters

If you protect only the route and not the object, an authenticated user can still access another user's resource.

That is why method-level security exists in addition to gateway authentication.

## 11. Observability: How You Know The System Is Healthy

BankFlow includes:

- metrics,
- health endpoints,
- Prometheus scraping,
- Grafana dashboards,
- correlation IDs,
- and SonarQube for code quality.

This is important because distributed systems fail in distributed ways.

You cannot debug them well with "print line 1, print line 2" logging.

The main observability setup lives across:

- [config/prometheus/prometheus.yml](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/config/prometheus/prometheus.yml)
- [config/grafana/provisioning](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/config/grafana/provisioning)
- Micrometer usage in service classes
- [CorrelationIdFilter.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-api-gateway/src/main/java/com/bankflow/gateway/filter/CorrelationIdFilter.java)

## 12. Testing Philosophy in BankFlow

The project uses three useful levels:

### Unit tests

Fast and isolated.  
Examples:

- auth service tests
- account command/query tests
- payment service and outbox tests

### Integration tests with Testcontainers

Used where realistic infrastructure matters:

- auth controller integration test
- account concurrency integration test
- payment saga integration test

### Why that mix is good

Only unit tests would miss real concurrency and infrastructure behavior.  
Only integration tests would be slow and painful.

So BankFlow uses both.

That is the correct balance.

## 13. Why This Design Is Strong, Without Pretending It Is Universal

Here is the honest engineering summary:

This design is strong for BankFlow because it intentionally demonstrates the failure-handling patterns that matter in a distributed banking platform:

- auth isolation,
- exact money arithmetic,
- optimistic locking,
- CQRS,
- Redis caching with eviction,
- saga choreography,
- outbox reliability,
- idempotency,
- Kafka retry/DLT,
- gateway protection,
- and full observability.

But that does not mean every real-world product should start this way.

A startup with one team and one product could absolutely begin with a modular monolith.

Why is this still a very good BankFlow design?

Because the project goal is not only to move money in one process.  
It is to model the engineering realities of distributed financial systems.

And for that goal, this architecture is well chosen.

## 14. The One Paragraph Explanation You Should Be Able To Say Out Loud

“BankFlow is a distributed banking platform where the gateway protects the public edge, auth-service manages identities and token lifecycle, account-service owns balances and audit-safe money state, payment-service owns transfer workflow using saga plus outbox plus idempotency, notification-service handles async customer communication through Kafka, Redis supports caching and short-lived state, MySQL stores durable domain data, and observability is built in through Prometheus, Grafana, and correlation IDs. The architecture is deliberately split so correctness, resilience, and service ownership stay clear.”

## 15. If You Are New, Read The Project In This Order

1. [README.md](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/README.md)
2. [docker-compose.infrastructure.yml](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/docker-compose.infrastructure.yml)
3. [bankflow-parent/pom.xml](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/pom.xml)
4. gateway `application.yml` and filters
5. auth controller + service + JWT service
6. account entity + command/query services
7. payment transaction + outbox + saga files
8. notification Kafka consumer + email service
9. tests
10. observability config

If you follow that order, the repo makes much more sense.
