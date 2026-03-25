# Mock System Design Interview: Fund Transfer System for BankFlow

This guide is written as if you are in a real interview answering:

> "Design a fund transfer system for a banking application that handles 100,000 transactions per day reliably."

The goal is not to sound academic. The goal is to sound like an engineer who has built a real system, understands failure modes, and can explain trade-offs clearly.

---

## 1. What A Strong Opening Sounds Like

Do not start with boxes.

Start with this:

> "Before I jump into architecture, I want to clarify the reliability and business guarantees, because for a banking transfer system correctness matters more than just throughput."
>
> "At 100,000 transactions per day, raw scale is very manageable for modern infrastructure. The real design challenge is preventing double debit, handling partial failure, preserving auditability, and making recovery predictable."
>
> "So I’ll first clarify the transfer scope, consistency expectations, retry behavior, and operational requirements, then I’ll propose an architecture."

That opening signals senior-level thinking because it says:

- you know the hard part is not QPS
- you prioritize correctness over premature scaling
- you understand that financial systems are failure-management systems

### Clarifying Questions To Ask First

Ask these before drawing anything:

1. "Are transfers only internal between accounts in our own bank, or do they include external rails like UPI, card, or NEFT?"
2. "What consistency guarantee do we need? Strong consistency at debit time? Eventual consistency for final completion?"
3. "What does 'reliably' mean here? No money loss, no duplicate debit, full audit trail, automatic recovery?"
4. "What is the acceptable user-facing latency for initiating a transfer?"
5. "Can we accept at-least-once message delivery if business operations are idempotent?"
6. "Do we need immutable audit logs and reconciliation reports for compliance?"
7. "If one leg of the transfer fails after debit, should the system auto-compensate or send to manual operations?"
8. "Will all clients come through an API gateway, or can internal services be called directly?"

### Why These Questions Matter

If you skip these questions, you risk designing the wrong system.

Examples:

- If external payment rails are involved, the saga becomes longer and less deterministic.
- If strong consistency is required across all legs, the business may be asking for something incompatible with loosely coupled microservices.
- If user latency must be under 300 ms, you probably return `PENDING` quickly and finish asynchronously.
- If regulations require non-repudiation and full traceability, audit logs and correlation IDs become mandatory, not optional.

---

## 2. The Short Version Of The Final Design

If the interviewer interrupts early and asks for the top-level answer, say this:

> "I would design this as a microservices-based transfer platform with an API Gateway at the edge, Auth Service for token lifecycle, Payment Service owning transfer intent and saga state, Account Service owning balances, Notification Service handling user communication asynchronously, Kafka for event choreography, MySQL as the source of truth, Redis for idempotency, blacklist, rate limiting, and caching, and an outbox pattern to solve the dual-write problem between database writes and Kafka publication."

That one sentence gives the interviewer your entire mental model.

---

## 3. Architecture Diagram

```text
                               +----------------------+
                               |  Prometheus /        |
                               |  Grafana             |
                               |  (HTTP metrics)      |
                               +----------^-----------+
                                          |
                                          | HTTP /actuator/prometheus
                                          |
+-------------------+     HTTPS     +-----+---------------------------+
| Mobile / Web App  +-------------->+ API Gateway                     |
| Client            |               | - JWT validation                |
|                   |               | - Rate limiting                 |
|                   |               | - Circuit breaker               |
|                   |               | - Correlation ID                |
+-------------------+               +-----+-------------+-------------+
                                          |             |
                                          | HTTP        | HTTP
                                          |             |
                                          v             v
                               +----------+---+   +-----+----------------+
                               | Auth Service |   | Payment Service      |
                               |              |   | - transfer intent    |
                               | MySQL        |   | - idempotency        |
                               | Redis        |   | - saga state         |
                               | JWT / refresh|   | - outbox             |
                               +------+-------+   +-----+----------------+
                                      |                 |
                                      | Redis           | MySQL TX
                                      |                 |
                                      v                 v
                               +------+-----------------+------+
                               | Redis                         |
                               | - token blacklist             |
                               | - rate limit counters         |
                               | - idempotency keys            |
                               | - query cache                 |
                               +------+-----------------+------+
                                      ^                 |
                                      | Redis           | Kafka publish
                                      |                 v
                               +------+-----------------+------+
                               | Kafka Broker / Topics         |
                               | payment.initiated             |
                               | account.debited               |
                               | account.credited              |
                               | account.debit.failed          |
                               | account.credit.failed         |
                               | compensation.requested        |
                               | payment.completed             |
                               | payment.failed                |
                               | payment.reversed              |
                               +------+-----------------+------+
                                      |                 |
                                      | Kafka consume   | Kafka consume
                                      v                 v
                               +------+-------+   +-----+----------------+
                               | Account      |   | Notification Service |
                               | Service      |   | - email/SMS/push     |
                               | - balances   |   | - idempotent consume |
                               | - CQRS       |   | - DLQ handling       |
                               | - optimistic |   | MySQL logs           |
                               |   locking    |   | MailHog local SMTP   |
                               +------+-------+   +----------+-----------+
                                      |                         |
                                      | MySQL                   | SMTP
                                      v                         v
                               +------+-------+          +------+------+
                               | MySQL        |          | MailHog     |
                               | bankflow_    |          | local email |
                               | account      |          | inspection  |
                               +--------------+          +-------------+
```

### How To Read This Diagram

- The client never talks to account-service or payment-service directly.
- The gateway is the only internet-facing component.
- Payment-service owns the business transaction.
- Account-service owns the balance.
- Kafka carries state transitions between services.
- Redis is not the source of truth for money. MySQL is.
- Notification is asynchronous because communication should never block money movement.

---

## 4. Component Walkthrough

This is the part you would spend about 8 minutes on in a real interview.

### 4.1 API Gateway

**What it does**

It is the single entry point for client traffic.

**Why it exists**

Because authentication, rate limiting, security headers, circuit breaking, and request tracing are cross-cutting concerns. If you repeat those inside every service, you create inconsistency and drift.

**Where it is implemented in BankFlow**

- `bankflow-parent/bankflow-api-gateway/src/main/java/com/bankflow/gateway/filter/JwtAuthenticationFilter.java`
- `bankflow-parent/bankflow-api-gateway/src/main/java/com/bankflow/gateway/service/GatewayJwtService.java`
- `bankflow-parent/bankflow-api-gateway/src/main/java/com/bankflow/gateway/config/GatewayConfig.java`
- `bankflow-parent/bankflow-api-gateway/src/main/java/com/bankflow/gateway/filter/CorrelationIdFilter.java`
- `bankflow-parent/bankflow-api-gateway/src/main/java/com/bankflow/gateway/filter/SecurityHeadersFilter.java`
- `bankflow-parent/bankflow-api-gateway/src/main/java/com/bankflow/gateway/controller/FallbackController.java`

**How it works**

1. Client sends request with JWT.
2. Gateway validates the JWT once.
3. Gateway checks blacklist in Redis.
4. Gateway injects trusted headers like `X-User-Id` and `X-User-Roles`.
5. Gateway applies rate limits and circuit breakers.
6. Gateway routes request to downstream service.

**Why this is a good fit**

Because downstream services should focus on their own domain logic, not edge security policy.

**Alternative**

Each service validates JWT on its own.

**Why not that here**

That is workable, but it duplicates logic, increases inconsistency risk, and makes policy changes slower.

---

### 4.2 Auth Service

**What it does**

Manages registration, login, logout, refresh-token rotation, failed login tracking, and account lockout.

**Where it lives**

- `AuthService`
- `JwtService`
- `User`
- `RefreshToken`

**Why it exists separately**

Identity and session lifecycle are a different bounded context from money transfer. Separating them keeps the payment path simpler and safer.

**Key design ideas**

- JWT access token for stateless auth
- refresh token stored in DB for revocation and rotation
- Redis blacklist for immediate logout
- lockout policy to reduce brute-force risk

**Why not use only stateless JWT with no DB refresh tokens**

Because then you cannot revoke sessions cleanly or detect refresh-token replay well.

---

### 4.3 Payment Service

**What it does**

Owns transfer intent, transaction state, idempotency, outbox rows, and saga progress.

**Where it lives**

- `PaymentService.initiateTransfer()`
- `PaymentSagaConsumer`
- `PaymentSagaService`
- `Transaction`
- `OutboxEvent`
- `OutboxPublisher`

**Why it exists**

Because transfer is a business workflow, not just two balance updates.

It needs to answer:

- was the request accepted?
- is it pending, completed, failed, or reversed?
- was compensation triggered?
- was the request a duplicate?

**Why payment-service should not update account balances directly**

Because then payment-service would own money state it does not control. In a clean design, account-service is the only writer of account balances.

---

### 4.4 Account Service

**What it does**

Owns the authoritative account record, current balance, audit logs, and account state such as active, frozen, or closed.

**Where it lives**

- `AccountCommandService`
- `AccountQueryService`
- `Account`
- `AccountAuditLog`
- `AccountSagaConsumer`

**Why it exists**

Because balance is the most sensitive mutable state in the system. One service must own it.

**Why BigDecimal matters**

Money must never use `double` or `float`. `BigDecimal` avoids floating-point precision error.

**Why `@Version` matters**

Because concurrent debits must not overwrite each other. Optimistic locking plus retry gives correct behavior under race conditions.

**Proof in BankFlow**

`AccountConcurrencyIT` exists specifically to prove that concurrent debits produce the correct final balance.

---

### 4.5 Notification Service

**What it does**

Consumes business events and sends customer communication asynchronously.

**Where it lives**

- `NotificationKafkaConsumer`
- `NotificationIdempotencyService`
- `KafkaConsumerConfig`
- `NotificationLog`
- `EmailService`

**Why it exists**

Because email delivery is not part of the critical financial commit path.

If email is slow or fails, the transfer should still complete correctly.

**Why DLQ exists**

Because some messages will keep failing. You do not want poison messages stuck in an infinite retry loop.

---

### 4.6 Kafka

**What it does**

Decouples services and carries state transitions.

**Why Kafka here**

Because payment and account processing should survive temporary outages and operate asynchronously.

**Important point**

Kafka does not magically give exactly-once business correctness.

That is why BankFlow also uses:

- outbox on the producer side
- idempotency on the consumer side
- durable transaction state in MySQL

---

### 4.7 Redis

**What it does**

Stores low-latency shared state:

- auth token blacklist
- gateway rate limits
- payment idempotency keys
- notification dedupe markers
- account query caches

**Why Redis is used**

Because these lookups are frequent, small, time-based, and latency-sensitive.

**What Redis does not do**

Redis does not own financial truth.

That remains in MySQL.

---

### 4.8 MySQL

**What it does**

Holds the source-of-truth state for:

- users
- refresh tokens
- accounts
- audit logs
- transactions
- outbox events
- notification logs

**Why relational database is a good choice here**

Because financial systems care about transactions, constraints, traceability, and joinable audit data.

**Alternative**

NoSQL for core money state.

**Why not in this design**

Possible, but usually adds more complexity than value for this scale and use case.

---

## 5. End-To-End Fund Transfer Flow

This is the most important operational explanation.

### Step 1: Client Initiates Transfer

Client calls gateway:

`POST /api/v1/payments/transfer`

Gateway:

- validates JWT
- checks blacklist
- enforces rate limit
- injects `X-User-Id`
- forwards to payment-service

### Step 2: Payment Service Validates And Deduplicates

`PaymentService.initiateTransfer()`:

1. checks Redis using idempotency key
2. validates amount and account IDs
3. creates `Transaction`
4. creates `OutboxEvent(PAYMENT_INITIATED)`
5. commits both in one DB transaction
6. caches response in Redis for duplicate retries
7. returns `PENDING`

Important point:

The API does not wait for the full transfer to finish. It acknowledges accepted intent.

### Step 3: Outbox Publisher Sends Event

`OutboxPublisher.publishPendingEvents()`:

1. reads pending outbox rows
2. publishes them to Kafka
3. waits for broker ack
4. marks event `PUBLISHED`

If Kafka is down:

- event stays pending or failed with retry count
- data is not lost

### Step 4: Account Service Debits Source Account

`AccountSagaConsumer` consumes `PAYMENT_INITIATED`:

1. checks idempotency marker
2. loads account from DB
3. validates account status
4. validates sufficient funds
5. updates balance with optimistic locking
6. writes audit log
7. evicts caches
8. publishes `ACCOUNT_DEBITED` or `ACCOUNT_DEBIT_FAILED`

### Step 5: Payment Service Reacts

If debit succeeded:

- `PaymentSagaConsumer.handleAccountDebited()` marks saga `ACCOUNT_DEBITED`
- writes outbox event to request destination credit

If debit failed:

- `PaymentSagaConsumer.handleDebitFailed()` marks transaction failed
- writes payment-failed outbox event

### Step 6: Account Service Credits Destination

Account-service consumes the credit request:

1. loads destination account
2. checks account state
3. credits balance
4. writes audit log
5. emits `ACCOUNT_CREDITED` or `ACCOUNT_CREDIT_FAILED`

### Step 7: Payment Service Completes Or Compensates

If credit succeeded:

- `handleAccountCredited()` marks transaction `COMPLETED`
- sets `completedAt`
- emits `PAYMENT_COMPLETED`

If credit failed:

- `handleCreditFailed()` moves saga to `COMPENSATING`
- emits `COMPENSATION_REQUESTED`

Then account-service reverses the source debit and emits `ACCOUNT_REVERSAL_COMPLETED`.

Finally:

- `handleReversalCompleted()` marks transaction `REVERSED`
- saga becomes `COMPENSATED`
- emits `PAYMENT_REVERSED`

### Step 8: Notification Service Sends Email

Notification-service consumes `PAYMENT_COMPLETED` or `PAYMENT_REVERSED`:

1. checks idempotency in Redis
2. renders email
3. sends via MailHog locally
4. logs notification result
5. acknowledges Kafka message

---

## 6. Why Saga Is The Right Pattern Here

### The Core Problem

A transfer touches multiple services:

- payment-service
- account-service
- notification-service

There is no single shared ACID transaction across them.

### Why Not 2-Phase Commit

Because 2PC across independent services and Kafka is heavyweight, blocking, and operationally painful.

It also does not map naturally to business compensation.

If destination credit fails, the correct banking behavior is not “pretend nothing happened.” It is:

- record the failure
- reverse the source debit
- keep an audit trail

That is exactly what saga gives you.

### What Makes The BankFlow Saga Good

- explicit business states in `Transaction.sagaStatus`
- durable event production via outbox
- idempotent consumers
- compensation path for partial success
- auditability of each step

---

## 7. Saga State Machine

```text
                 +------------------+
                 |     STARTED      |
                 +---------+--------+
                           |
                           | ACCOUNT_DEBITED event
                           v
                 +---------+--------+
                 | ACCOUNT_DEBITED  |
                 +----+---------+---+
                      |         |
          ACCOUNT_    |         | ACCOUNT_CREDIT_FAILED
          CREDITED    |         |
                      v         v
             +--------+--+   +--+-------------+
             | COMPLETED |   | COMPENSATING   |
             +-----------+   +-------+--------+
                                     |
                                     | ACCOUNT_REVERSAL_COMPLETED
                                     v
                               +-----+------+
                               | COMPENSATED|
                               +------------+

If debit fails from STARTED:

STARTED ---> FAILED
```

### How To Explain This In An Interview

Say this:

> "I model the transfer as a business state machine, not just a chain of API calls. That is important because failures are not edge cases in distributed systems. They are normal paths. By making `STARTED`, `ACCOUNT_DEBITED`, `COMPENSATING`, `COMPLETED`, and `COMPENSATED` explicit states, I can reason about recovery, reconciliation, and support investigation."

That sentence is strong because it shows you think in terms of recoverable workflow state.

---

## 8. Reliability Guarantees And Failure Scenarios

This is where senior answers separate themselves.

### 8.1 If Kafka Goes Down

What happens:

- transfer request can still be accepted by payment-service
- transaction and outbox row are saved atomically
- outbox publisher cannot publish immediately
- event remains pending for retry

What does **not** happen:

- money is not silently lost
- transaction is not forgotten

What the user sees:

- likely `PENDING` longer than normal

### 8.2 If Payment Service Crashes After DB Commit

Safe because:

- `Transaction` is already stored
- `OutboxEvent` is already stored
- publisher resumes later

### 8.3 If Account Service Receives Same Event Twice

Possible because Kafka is at-least-once.

Protected by:

- idempotency marker in Redis
- saga state checks
- optimistic locking on account balance

### 8.4 If Debit Succeeds But Credit Fails

Safe because:

- payment-service moves saga to compensating
- account-service reverses source debit
- final status is `REVERSED` / `COMPENSATED`

### 8.5 If Notification Fails

Transfer still completes.

Why:

Notification is a side effect, not a financial state transition.

Notification failure goes to retry or DLT, not transfer rollback.

---

## 9. How BankFlow Prevents Double Charge

This is one of the most important interview answers.

Do not say “we guarantee exactly once” casually.

Say this:

> "In distributed systems I do not rely on one mechanism to prevent duplicate charge. I layer protections."

Those layers in BankFlow are:

1. **API idempotency key**
   - implemented in `PaymentService`
   - duplicate request returns same cached response

2. **Database uniqueness**
   - `Transaction.idempotencyKey` is unique
   - DB is the final safety line

3. **Consumer idempotency**
   - saga consumers dedupe repeated Kafka events

4. **Optimistic locking**
   - account balance writes reject stale concurrent updates

5. **Audit logs**
   - if something still goes wrong, legal trace exists

### Why This Answer Is Strong

Because real reliability comes from overlapping controls, not one magical feature.

---

## 10. CQRS In Account Service

### What It Means Here

BankFlow splits reads and writes:

- `AccountCommandService` handles create/debit/credit/status update
- `AccountQueryService` handles reads and cache-backed lookup

### Why It Helps

Write-side rules are strict:

- always fresh DB state
- concurrency control
- audit logs
- no stale cache risk

Read-side can optimize:

- cache account details for 5 minutes
- cache balance for 30 seconds
- avoid caching statement because compliance-sensitive

### Why This Is Good For Banking

Because read speed and write correctness do not have the same constraints.

One of the biggest signs of maturity is knowing that not all data paths deserve the same consistency model.

---

## 11. Redis Strategy Explained Like A Senior Engineer

Redis is used in four very different ways in BankFlow:

### 11.1 Blacklist Store

Used by auth and gateway.

Why:

Access tokens are stateless. Blacklist gives immediate revocation using `jti`.

### 11.2 Idempotency Store

Used by payment-service.

Why:

Client retries must not create duplicate transfers.

### 11.3 Notification Dedupe

Used by notification-service.

Why:

Kafka redelivery must not send duplicate emails.

### 11.4 Read Cache

Used by account-service.

Why:

Most account reads do not need to hit MySQL every time.

### Why Redis Is Perfect Here

Because the common property is not “all this data is the same.” The common property is:

- small
- high-frequency
- low-latency
- TTL-based
- not the final financial truth

That is exactly Redis territory.

---

## 12. Outbox Pattern: The Answer Interviewers Love

### The Problem

Naive code:

1. save transaction in DB
2. publish event to Kafka

This fails if one succeeds and the other does not.

### BankFlow Answer

Store `Transaction` and `OutboxEvent` in one database transaction.

Then a background publisher sends outbox rows to Kafka.

### Why This Matters So Much

Because when the interviewer asks:

> "Service crashes after debit but before Kafka publish. What happens to the money?"

You can say:

> "We never trust an in-memory publish step for correctness. We persist both business state and event intent durably, then publish asynchronously from outbox. That is exactly why the outbox pattern exists."

That answer sounds real because it is real.

---

## 13. Security Story

### At The Edge

Gateway handles:

- JWT validation
- blacklist check
- rate limiting
- security headers
- correlation IDs

### Inside Services

Services still enforce authorization rules:

- account ownership checks
- payment participant checks

### Why Both Layers Matter

Because URL-level security says who can reach an endpoint.

Method-level security says what that caller is allowed to access.

Both are required.

### Example In BankFlow

- `AccountSecurityService`
- `PaymentSecurityService`

These implement ownership and participation checks using real persisted data.

---

## 14. Scaling From 100,000/Day To 10 Million/Day

At 100,000/day, you do not need exotic architecture.

At 10 million/day, you start thinking differently.

### What Changes First

1. **Kafka partitioning strategy**
   - choose good keys, usually account or transaction related
   - increase partitions with consumer scaling

2. **Database topology**
   - write primary, read replicas
   - separate operational and analytical workloads

3. **Hot account protection**
   - some accounts may become concurrency hotspots
   - optimistic locking may need more targeted mitigation

4. **Outbox throughput**
   - poll more efficiently
   - maybe use CDC or batch optimization if needed

5. **Observability and reconciliation**
   - more automation
   - anomaly detection
   - replay tooling

6. **Operational separation**
   - isolate notification and reporting from core money movement

### Important Interview Insight

Scaling is not just “add pods.”

It is:

- preserving ordering where needed
- preventing hotspot collapse
- maintaining auditability
- keeping recovery predictable

---

## 15. How To Answer The Hard Questions

### "Why Not A Monolith?"

Good answer:

> "A monolith would actually be a valid starting point at this scale if the team were small and the domain still evolving. I chose services here because identity, balance management, transfer workflow, and notification have very different failure modes and scaling patterns. The key is not that microservices are always better. The key is that for BankFlow, they map cleanly to bounded contexts and operational isolation needs."

Why this is strong:

You are not dogmatic.

### "Why Not Just Synchronous REST?"

Good answer:

> "For reads and some control paths, synchronous REST is fine. For multi-step transfer completion, asynchronous eventing is safer because temporary slowness or downtime in a downstream service should not force the caller to hold an open request or lose the operation."

### "Why Not Exactly Once Kafka?"

Good answer:

> "Kafka’s exactly-once semantics help at the broker and producer-consumer transaction boundary, but they do not eliminate business-level duplicate risk across services, databases, retries, and side effects. I still design for idempotency at the application level."

---

## 16. How To Investigate A Double Charge Complaint

This is an excellent real-world question.

Walk through it like this:

1. identify customer, transaction reference, time window
2. search logs using correlation ID if available
3. inspect `Transaction` table for duplicate idempotency keys or duplicate business rows
4. inspect `AccountAuditLog` for actual number of debits
5. inspect saga state and related outbox events
6. inspect Redis idempotency key behavior
7. inspect Kafka redelivery or consumer duplicate path
8. determine whether it is:
   - actual double debit
   - stale UI
   - duplicate notification
   - delayed compensation
9. if actual financial duplicate:
   - stop further processing
   - reverse via auditable path
   - document root cause
   - patch the failed guard

### Why This Answer Is Strong

Because it is forensic, not emotional.

You do not jump to refund first. You establish the exact failure class.

---

## 17. What To Say When You Are Stuck

These phrases are professional and safe:

- "I don’t want to guess on correctness for a banking system, so I’ll state my assumption explicitly."
- "I’m not fully certain on that infrastructure detail, but I can explain the trade-offs and choose the safer option."
- "There are two reasonable approaches here. I’ll compare them, then pick one based on reliability and operational simplicity."
- "I’d validate the exact implementation detail with the team, but architecturally I would still preserve these guarantees..."

### Good Thinking-Out-Loud Structure

Use this template:

1. state assumption
2. state risk
3. state mitigation
4. state chosen design

Example:

> "I’m assuming at-least-once delivery from Kafka. That means duplicates are possible. Because this is money movement, I won’t rely on the broker for correctness. I’ll use API idempotency, durable transaction state, outbox, consumer dedupe, and optimistic locking."

That sounds senior because it is structured, calm, and practical.

---

## 18. A Strong Final Summary To End The Interview

Use this if the interviewer says, "Can you summarize?"

> "I’d design the transfer system around correctness first: gateway for edge security and throttling, payment-service owning transfer intent and saga state, account-service owning balances with optimistic locking, Kafka for asynchronous choreography, outbox to solve DB-to-broker dual writes, Redis for idempotency and blacklist checks, and audit logs for traceability. At 100,000 transactions per day this is comfortably scalable, but more importantly it is recoverable, explainable, and safe under failure."

That is a strong ending because it combines:

- business correctness
- architecture
- failure handling
- scale
- confidence

---

## 19. The Deepest Insight To Remember

If you remember only one thing from this whole guide, remember this:

> In banking, the best architecture is not the one with the fanciest components. It is the one that makes wrong money movement hardest to happen, easiest to detect, and safest to repair.

That is the real system design answer.
