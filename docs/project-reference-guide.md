# BankFlow Complete Project Reference Guide

This guide is for understanding BankFlow as a real system, not just as a collection of files.

It is written for someone who may be new to distributed systems, but it stays technically honest. The goal is to answer the natural questions a good engineer asks:

- What is this part doing?
- Why does it exist?
- Where is it implemented?
- When is it used?
- How does it work?
- What problem does it prevent?
- What alternatives were possible?
- Why is this design a good fit for BankFlow?

If you read this guide fully, the repository becomes much easier to navigate.

## 1. What BankFlow Really Is

BankFlow is a distributed banking platform built as a set of Spring Boot services.

The business responsibilities are split like this:

- `api-gateway` is the single public entry point.
- `auth-service` owns users, login, JWT, refresh tokens, and account lockout.
- `account-service` owns accounts, balances, balance changes, and account audit logs.
- `payment-service` owns transfer initiation, payment transaction state, idempotency, saga state, and outbox rows.
- `notification-service` owns asynchronous notifications like payment-success and account-created emails.
- `bankflow-common` holds shared contracts, errors, events, constants, and shared utility logic.

You can see the module layout in [bankflow-parent/pom.xml](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/pom.xml).

The most important thing to understand is this:

BankFlow is not difficult because it has many entities. It is difficult because it tries to keep money movement correct when requests are retried, services fail, Kafka redelivers, and multiple updates happen at the same time.

That is why the project uses patterns like:

- Saga choreography
- Outbox
- Idempotency
- Optimistic locking
- CQRS
- Redis caching
- JWT blacklisting and refresh-token rotation
- Kafka retry + DLT
- Circuit breaker
- Rate limiting

## 2. How The Repository Is Organized

There are three practical layers.

### Infrastructure Layer

Files:

- [docker-compose.infrastructure.yml](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/docker-compose.infrastructure.yml)
- [config](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/config)

This layer provides:

- MySQL
- Redis
- Kafka
- MailHog
- Prometheus
- Grafana
- SonarQube
- PostgreSQL for SonarQube

### Application Layer

Files:

- [bankflow-parent](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent)

This is the actual Java application code.

### Developer Experience Layer

Files:

- [README.md](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/README.md)
- [docs/local-setup.md](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/docs/local-setup.md)
- [postman](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/postman)
- [start-infra.sh](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/start-infra.sh)
- [start-infra.bat](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/start-infra.bat)

This layer makes the system runnable and easy to demonstrate locally.

## 3. The Runtime Story In One Simple Flow

The easiest way to understand BankFlow is:

1. A client sends an HTTP request to the gateway.
2. The gateway validates the token and adds trusted identity headers.
3. The gateway routes the request to the correct service.
4. That service performs local business logic against MySQL, Redis, or both.
5. If other services need to react, Kafka events are published.
6. Other services consume those events asynchronously.
7. Metrics, logs, and dashboards let you inspect what happened.

That runtime model explains most of the project.

## 4. Why The System Is Split Into Services

The services are split by business responsibility, not just by technical style.

### Auth Service

Main files:

- [AuthController.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-auth-service/src/main/java/com/bankflow/auth/controller/AuthController.java)
- [AuthService.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-auth-service/src/main/java/com/bankflow/auth/service/AuthService.java)
- [JwtService.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-auth-service/src/main/java/com/bankflow/auth/service/JwtService.java)

Why separate it?

- identity rules change independently,
- auth traffic is different from payment traffic,
- and security lifecycle logic should not be mixed with balance logic.

### Account Service

Main files:

- [AccountController.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-account-service/src/main/java/com/bankflow/account/controller/AccountController.java)
- [AccountCommandService.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-account-service/src/main/java/com/bankflow/account/service/AccountCommandService.java)
- [AccountQueryService.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-account-service/src/main/java/com/bankflow/account/service/AccountQueryService.java)
- [Account.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-account-service/src/main/java/com/bankflow/account/entity/Account.java)

Why separate it?

- balance correctness is a domain boundary,
- money mutations need their own rules,
- and no other service should directly own account balances.

### Payment Service

Main files:

- [PaymentController.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-payment-service/src/main/java/com/bankflow/payment/controller/PaymentController.java)
- [PaymentService.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-payment-service/src/main/java/com/bankflow/payment/service/PaymentService.java)
- [PaymentSagaService.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-payment-service/src/main/java/com/bankflow/payment/service/PaymentSagaService.java)
- [PaymentSagaConsumer.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-payment-service/src/main/java/com/bankflow/payment/messaging/PaymentSagaConsumer.java)

Why separate it?

- transfer workflow spans multiple systems,
- it needs its own reliability model,
- and it should own transaction state, not account-service.

### Notification Service

Main files:

- [NotificationKafkaConsumer.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-notification-service/src/main/java/com/bankflow/notification/messaging/NotificationKafkaConsumer.java)
- [NotificationIdempotencyService.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-notification-service/src/main/java/com/bankflow/notification/service/NotificationIdempotencyService.java)
- [EmailService.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-notification-service/src/main/java/com/bankflow/notification/service/EmailService.java)

Why separate it?

- notifications are side effects,
- they should not slow down transfers,
- and retrying email should not block money correctness.

### API Gateway

Main files:

- [application.yml](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-api-gateway/src/main/resources/application.yml)
- [JwtAuthenticationFilter.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-api-gateway/src/main/java/com/bankflow/gateway/filter/JwtAuthenticationFilter.java)
- [GatewayJwtService.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-api-gateway/src/main/java/com/bankflow/gateway/service/GatewayJwtService.java)

Why separate it?

- one public entry point is easier to protect,
- routing stays centralized,
- and downstream services stay focused on domain logic.

## 5. Could This Have Been A Monolith?

Yes.

A modular monolith is often the best first architecture for a real startup.

So why is this microservice design still valid?

Because BankFlow is intentionally trying to model distributed-system realities:

- cross-service consistency,
- event-driven workflows,
- compensation,
- outbox reliability,
- gateway-edge security,
- and async notification behavior.

Those ideas are much harder to demonstrate honestly in a single-process monolith.

## 6. API Gateway: What, Why, Where, When, How

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

### What

The gateway is the public boundary of the platform.

It handles:

- route forwarding,
- JWT validation,
- access-token blacklist checks,
- rate limiting,
- circuit-breaker behavior,
- request correlation IDs,
- and response security headers.

### Why

Without a gateway, every service would have to repeat:

- token parsing,
- auth error handling,
- rate limiting,
- public-route logic,
- and edge security behavior.

That duplication becomes messy very quickly.

### Where

The routing and resilience rules are in [application.yml](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-api-gateway/src/main/resources/application.yml).

The JWT path is implemented in [JwtAuthenticationFilter.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-api-gateway/src/main/java/com/bankflow/gateway/filter/JwtAuthenticationFilter.java) and [GatewayJwtService.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-api-gateway/src/main/java/com/bankflow/gateway/service/GatewayJwtService.java).

### When

The gateway runs on every incoming request from the client. It is always the first application component in the request path.

### How

The filter checks whether the path is public. If it is not public, it:

1. reads the `Authorization` header,
2. validates the JWT,
3. checks whether the token is blacklisted,
4. extracts the user ID and roles,
5. forwards them as trusted internal headers such as `X-User-Id` and `X-User-Roles`.

That design lets internal services focus on authorization and business logic instead of repeating token parsing.

### Why this is a good fit

For BankFlow, the gateway is the right place for shared protection because:

- there is one public entry point,
- policies stay consistent,
- and internal services stay simpler.

### Alternative

Alternative: make every service validate JWT itself.

Why not here?

- duplicated code,
- duplicated crypto work,
- more configuration drift,
- harder to change auth policy centrally.

### Honest caveat

This header-trust model is acceptable inside a controlled internal network. In a stricter production environment, you would often add mTLS or service identity so internal spoofing is harder.

## 7. Auth Service: Identity And Token Lifecycle

Main files:

- [AuthController.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-auth-service/src/main/java/com/bankflow/auth/controller/AuthController.java)
- [AuthService.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-auth-service/src/main/java/com/bankflow/auth/service/AuthService.java)
- [JwtService.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-auth-service/src/main/java/com/bankflow/auth/service/JwtService.java)
- [User.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-auth-service/src/main/java/com/bankflow/auth/entity/User.java)
- [RefreshToken.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-auth-service/src/main/java/com/bankflow/auth/entity/RefreshToken.java)
- [Role.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-auth-service/src/main/java/com/bankflow/auth/entity/Role.java)

### What

Auth service owns:

- registration,
- login,
- JWT access tokens,
- refresh tokens,
- logout,
- logout from all devices,
- user roles,
- failed-login tracking,
- lockout window logic.

### Why

Auth is a separate problem from money movement. Keeping them together usually makes both worse.

This separation helps because:

- auth logic changes often,
- auth traffic has different characteristics,
- and password/token lifecycle is a security problem, not an account-balance problem.

### Where

The business logic is centered in [AuthService.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-auth-service/src/main/java/com/bankflow/auth/service/AuthService.java).

JWT generation and blacklist logic live in [JwtService.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-auth-service/src/main/java/com/bankflow/auth/service/JwtService.java).

### When

This service is used during:

- register,
- login,
- refresh,
- logout,
- profile lookup,
- and any future user security lifecycle event.

### How

Login works like this:

1. find user by username or email,
2. check active status,
3. check lock window,
4. unlock if lock window expired,
5. compare password using BCrypt,
6. increment failure count on bad password,
7. lock after repeated failures,
8. reset counters on success,
9. create access token,
10. create DB-backed refresh token,
11. return both to client.

### Why refresh tokens are stored in DB

Because access tokens are stateless and short-lived, while refresh tokens need lifecycle control.

The DB storage gives BankFlow:

- revocation,
- rotation,
- expiry,
- logout-all-devices,
- and theft detection support.

### Why `jti` matters

The JWT includes `jti` so one exact access token can be blacklisted in Redis.

Without `jti`, token-level revocation is far less precise.

### Why this is a good fit

This design is strong because it balances:

- fast stateless access tokens,
- revocable refresh tokens,
- Redis blacklist support,
- and realistic account lockout behavior.

### Alternatives

Alternative: pure server-side sessions.

Why not here?

- valid for many apps,
- but less aligned with distributed gateway-based architecture.

Alternative: opaque tokens with introspection.

Why not here?

- stronger central control,
- but more infrastructure cost for this project.

## 8. Account Service: Money State, Concurrency, And Audit

Main files:

- [AccountController.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-account-service/src/main/java/com/bankflow/account/controller/AccountController.java)
- [Account.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-account-service/src/main/java/com/bankflow/account/entity/Account.java)
- [AccountAuditLog.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-account-service/src/main/java/com/bankflow/account/entity/AccountAuditLog.java)
- [AccountCommandService.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-account-service/src/main/java/com/bankflow/account/service/AccountCommandService.java)
- [AccountQueryService.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-account-service/src/main/java/com/bankflow/account/service/AccountQueryService.java)
- [AccountSagaConsumer.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-account-service/src/main/java/com/bankflow/account/messaging/AccountSagaConsumer.java)
- [CacheConfig.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-account-service/src/main/java/com/bankflow/account/config/CacheConfig.java)
- [AccountSecurityService.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-account-service/src/main/java/com/bankflow/account/security/AccountSecurityService.java)

### What

Account service owns:

- account creation,
- account number generation,
- debit,
- credit,
- balance lookup,
- account status updates,
- statement data,
- audit logs.

### Why

This service is the source of truth for account balance and account status.

That means:

- no other service should directly update balances,
- auditability must exist,
- money math must be exact,
- concurrent updates must be safe.

### Where

Write logic is in [AccountCommandService.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-account-service/src/main/java/com/bankflow/account/service/AccountCommandService.java).

Read logic is in [AccountQueryService.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-account-service/src/main/java/com/bankflow/account/service/AccountQueryService.java).

### When

This service is used whenever:

- a user opens an account,
- a balance is checked,
- a transfer needs debit or credit,
- an administrator changes account status,
- or a statement is requested.

### How

Two design choices matter most here.

First, money is stored as `BigDecimal` in [Account.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-account-service/src/main/java/com/bankflow/account/entity/Account.java), because floating point values are unsafe for money.

Second, `@Version` is used on `Account` for optimistic locking. That means stale concurrent writes fail instead of silently overwriting each other.

### Why CQRS is used

The service separates write operations and read operations because they want different things:

- writes need fresh authoritative state,
- reads want speed and can use cache.

That is why [AccountCommandService.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-account-service/src/main/java/com/bankflow/account/service/AccountCommandService.java) and [AccountQueryService.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-account-service/src/main/java/com/bankflow/account/service/AccountQueryService.java) are separated.

### Why audit logs exist

[AccountAuditLog.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-account-service/src/main/java/com/bankflow/account/entity/AccountAuditLog.java) records balance-affecting actions because every banking mutation should be explainable later.

Without audit logs, you can tell what the current balance is, but not how it got there.

### Why this is a good fit

This design is strong because it combines:

- exact money representation,
- concurrency protection,
- auditability,
- ownership-based authorization,
- and selective read caching.

### Alternatives

Alternative: let payment-service update balances directly.

Why not?

- breaks domain ownership,
- creates tighter coupling,
- makes correctness harder to reason about.

Alternative: use pessimistic locking.

Why not here?

- valid in some systems,
- but more blocking and less elegant than optimistic retry for this project.

## 9. Payment Service: Distributed Transfer Logic

Main files:

- [PaymentController.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-payment-service/src/main/java/com/bankflow/payment/controller/PaymentController.java)
- [PaymentService.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-payment-service/src/main/java/com/bankflow/payment/service/PaymentService.java)
- [PaymentTransactionWriter.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-payment-service/src/main/java/com/bankflow/payment/service/PaymentTransactionWriter.java)
- [PaymentSagaService.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-payment-service/src/main/java/com/bankflow/payment/service/PaymentSagaService.java)
- [PaymentSagaConsumer.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-payment-service/src/main/java/com/bankflow/payment/messaging/PaymentSagaConsumer.java)
- [Transaction.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-payment-service/src/main/java/com/bankflow/payment/entity/Transaction.java)
- [OutboxEvent.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-payment-service/src/main/java/com/bankflow/payment/entity/OutboxEvent.java)
- [OutboxPublisher.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-payment-service/src/main/java/com/bankflow/payment/service/OutboxPublisher.java)

### What

Payment service owns:

- transfer initiation,
- transaction state,
- idempotency checks,
- outbox row creation,
- saga status changes,
- reaction to debit success, debit failure, credit failure, and reversal completion.

### Why

This is the service where a user action becomes a durable business transfer process.

It is the right place to own:

- the transfer reference,
- the initiated amount,
- failure reason,
- lifecycle status,
- and the logic that drives the whole transfer.

### Where

Normal transfer entry starts in [PaymentService.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-payment-service/src/main/java/com/bankflow/payment/service/PaymentService.java).

Saga reactions happen in [PaymentSagaConsumer.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-payment-service/src/main/java/com/bankflow/payment/messaging/PaymentSagaConsumer.java) and [PaymentSagaService.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-payment-service/src/main/java/com/bankflow/payment/service/PaymentSagaService.java).

### When

This service is used during:

- transfer request creation,
- payment lookup,
- saga progression,
- compensation,
- and duplicate-request handling.

### How

`PaymentService.initiateTransfer(...)`:

1. checks Redis for the idempotency key,
2. validates request,
3. creates the `Transaction`,
4. creates the matching `OutboxEvent`,
5. stores the response in Redis,
6. returns pending transfer state.

The actual event publication is not done inline. It is done later by [OutboxPublisher.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-payment-service/src/main/java/com/bankflow/payment/service/OutboxPublisher.java).

### Why Saga is used

A transfer spans multiple services and databases. One normal SQL transaction cannot safely cover the whole thing.

So BankFlow uses Saga choreography:

- initiate,
- debit source,
- request credit,
- complete on success,
- compensate on failure.

### Why Outbox is used

Because saving DB state and publishing to Kafka are two separate systems. That is the dual write problem.

BankFlow solves it by storing both `Transaction` and `OutboxEvent` in one local DB transaction.

### Why idempotency is used

Payment retries are normal. Duplicate charging is unacceptable.

So the first request and the retry should produce the same business result, not two transfers.

### Why this is a good fit

This design is strong because it is built for failure, not just success.

It expects:

- user retries,
- Kafka redelivery,
- service crashes,
- and compensation scenarios.

### Alternatives

Alternative: synchronous REST-only workflow.

Why not here?

- tighter coupling,
- lower resilience,
- harder recovery if one dependency is down.

Alternative: 2-phase commit.

Why not here?

- too heavy,
- bad fit across heterogeneous systems,
- rarely the preferred modern answer for service-level banking workflows.

## 10. Notification Service: Side Effects Without Blocking Transfers

Main files:

- [NotificationKafkaConsumer.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-notification-service/src/main/java/com/bankflow/notification/messaging/NotificationKafkaConsumer.java)
- [KafkaConsumerConfig.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-notification-service/src/main/java/com/bankflow/notification/config/KafkaConsumerConfig.java)
- [NotificationIdempotencyService.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-notification-service/src/main/java/com/bankflow/notification/service/NotificationIdempotencyService.java)
- [NotificationLogService.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-notification-service/src/main/java/com/bankflow/notification/service/NotificationLogService.java)
- [EmailService.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-notification-service/src/main/java/com/bankflow/notification/service/EmailService.java)

### What

Notification service consumes Kafka events and turns them into customer-facing side effects such as emails.

### Why

Notifications should not sit inside the critical path of money movement.

If email sending is slow or broken:

- the transfer should still be correct,
- the payment should still complete,
- and notification can be retried later.

That is exactly why this service is separate.

### Where

Kafka retry and dead-letter behavior are configured in [KafkaConsumerConfig.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-notification-service/src/main/java/com/bankflow/notification/config/KafkaConsumerConfig.java).

Email rendering and sending happen in [EmailService.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-notification-service/src/main/java/com/bankflow/notification/service/EmailService.java).

### When

This service is used after business events occur:

- payment completed,
- payment reversed,
- account created.

### How

The consumer:

1. reads Kafka message,
2. checks idempotency in Redis,
3. tries to send notification,
4. logs the result,
5. acknowledges only after successful processing.

If processing keeps failing, the message goes to a dead-letter topic instead of retrying forever.

### Why manual ack matters

If Kafka committed the message before processing completed, a crash could lose the notification.

Manual ack avoids that.

### Why DLT matters

Poison messages should not loop forever or block healthy traffic. DLT gives failed events a controlled place to land.

### Why this is a good fit

This is a strong design because it treats notifications as important, but not as part of the financial correctness boundary.

That is exactly the right mental model.

## 11. Shared Contracts In bankflow-common

Main files:

- [ApiResponse.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-common/src/main/java/com/bankflow/common/api/ApiResponse.java)
- [ErrorCode.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-common/src/main/java/com/bankflow/common/error/ErrorCode.java)
- [GlobalExceptionHandler.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-common/src/main/java/com/bankflow/common/handler/GlobalExceptionHandler.java)
- [KafkaTopics.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-common/src/main/java/com/bankflow/common/kafka/KafkaTopics.java)
- [CacheKeys.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-common/src/main/java/com/bankflow/common/cache/CacheKeys.java)
- [DataMaskingUtil.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-common/src/main/java/com/bankflow/common/util/DataMaskingUtil.java)

### What

This module holds contracts that multiple services should share safely:

- response shape,
- error codes,
- event payloads,
- enums,
- topic names,
- cache keys,
- masking utilities.

### Why

In microservices, some things should be shared and some should not.

Good things to share:

- message contracts,
- constants,
- error enums,
- utility types.

Bad things to share:

- database tables,
- direct repository access,
- cross-service persistence logic.

BankFlow shares contracts, not data ownership. That is the correct line.

## 12. Security Model Across The Whole Platform

Security in BankFlow is layered on purpose.

### Gateway layer

Handled by the gateway:

- JWT validation,
- token blacklist check,
- rate limiting,
- circuit breaker behavior,
- response headers,
- correlation IDs.

### Service layer

Handled inside services:

- object-level authorization with `@PreAuthorize`,
- ownership checks like [AccountSecurityService.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-account-service/src/main/java/com/bankflow/account/security/AccountSecurityService.java),
- participant checks like [PaymentSecurityService.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-payment-service/src/main/java/com/bankflow/payment/security/PaymentSecurityService.java),
- DTO validation,
- data masking,
- suppressed stack traces in client responses.

### Why both layers matter

Because "authenticated" is not the same as "authorized to see this specific account or transaction."

The gateway proves identity. The service still enforces ownership.

That is a strong design.

## 13. Observability: How You Know The System Is Healthy

Main parts:

- Prometheus scrape config:
  [config/prometheus/prometheus.yml](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/config/prometheus/prometheus.yml)
- Grafana provisioning:
  [config/grafana/provisioning](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/config/grafana/provisioning)
- correlation ID propagation:
  [CorrelationIdFilter.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-api-gateway/src/main/java/com/bankflow/gateway/filter/CorrelationIdFilter.java)

### Why it matters

Distributed systems fail in distributed ways.

Without:

- metrics,
- dashboards,
- health endpoints,
- and logs tied together by a correlation ID,

you are mostly guessing when something breaks.

BankFlow includes observability early, which is exactly the right habit.

## 14. Testing Philosophy

BankFlow uses a mix of:

- unit tests for fast feedback,
- integration tests where real infrastructure matters,
- concurrency tests where correctness depends on simultaneous execution.

Why this is good:

- unit tests keep feedback fast,
- integration tests catch environment-sensitive logic,
- concurrency tests prove correctness beyond simple method-level testing.

That mix is much stronger than relying on only one test style.

## 15. Why This Design Is Strong Without Pretending It Is Universal

This is the honest summary.

BankFlow is a strong distributed banking design because it intentionally solves the failure modes that matter in financial systems:

- duplicate requests,
- stale concurrent writes,
- cross-service consistency gaps,
- consumer crashes,
- downstream outages,
- stale caches,
- weak token lifecycle,
- poor observability.

But that does not mean every company should start with this architecture.

A modular monolith is still a very good choice for many teams.

Why is this architecture still the right one for BankFlow?

Because the purpose of BankFlow is to model distributed-system banking behavior and show the patterns required to make that safe.

For that goal, this architecture is a very good fit.

## 16. How To Read The Code If You Are New

Read the repo in this order:

1. [README.md](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/README.md)
2. [docker-compose.infrastructure.yml](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/docker-compose.infrastructure.yml)
3. [bankflow-parent/pom.xml](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/pom.xml)
4. gateway route/security files
5. auth service
6. account service
7. payment service
8. notification service
9. tests
10. observability config

That order reduces confusion a lot.

## 17. One Paragraph Summary You Should Be Able To Say Out Loud

"BankFlow is a distributed banking platform where the gateway protects the public edge, auth-service manages identity and token lifecycle, account-service owns balances and audit-safe account state, payment-service owns transfer workflow using saga plus outbox plus idempotency, notification-service handles asynchronous customer communication, Redis stores fast-changing cache and workflow state, MySQL stores durable business data, and observability is built in through metrics, dashboards, and correlation IDs. The project is designed around correctness under failure, not just successful requests."
