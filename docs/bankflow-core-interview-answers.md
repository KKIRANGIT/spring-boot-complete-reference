# BankFlow Core Interview Answers

This file is a speaking guide for the most important BankFlow architecture questions.

The goal is not just to memorize words. The goal is to understand the idea deeply enough that you can explain it calmly, in your own language, even under interview pressure.

---

## 1. Draw Full BankFlow Architecture From Memory In 2 Minutes

### Short spoken version

> "BankFlow has a client talking to an API Gateway. The gateway handles JWT validation, rate limiting, circuit breaking, correlation IDs, and routes traffic to five backend services: auth-service, account-service, payment-service, notification-service, and shared observability endpoints. Auth-service manages login and token lifecycle with MySQL and Redis. Account-service owns balances and audit logs in MySQL, uses Redis for read caching, and consumes Kafka events for debit, credit, and compensation. Payment-service owns transfer intent, saga state, idempotency, and the outbox table in MySQL, and publishes transfer workflow events to Kafka. Notification-service consumes business events from Kafka, deduplicates them with Redis, logs notification outcomes in MySQL, and sends local email through MailHog. Prometheus scrapes all services, Grafana visualizes metrics, and SonarQube is used for code quality locally." 

### Architecture from memory

```text
[ Client / Mobile App ]
          |
          | HTTPS
          v
[ API Gateway ]
  - JWT validation
  - Rate limiting
  - Circuit breaker
  - Correlation ID
          |
          | HTTP
          +------------------------------+
          |                              |
          v                              v
[ Auth Service ]                    [ Payment Service ]
  MySQL: bankflow_auth                MySQL: bankflow_payment
  Redis: blacklist                    Redis: idempotency
  JWT + refresh                       Outbox + Saga state
          |                              |
          | Redis                        | Kafka
          v                              v
        [ Redis ] <-----------------> [ Kafka ]
          ^                               ^
          | Redis                         | Kafka
          |                               |
[ API Gateway rate limit ]         [ Account Service ]
                                    MySQL: bankflow_account
                                    Redis: account/balance cache
                                    CQRS + optimistic locking
                                            |
                                            | Kafka
                                            v
                                   [ Notification Service ]
                                    MySQL: notification logs
                                    Redis: notification dedupe
                                    MailHog via SMTP

[ Prometheus ] <-- HTTP metrics -- all services
[ Grafana ] <-- Prometheus
[ SonarQube ] <-- Maven analysis
```

### What each box means in plain English

- `API Gateway`: the front door. It protects everything behind it.
- `Auth Service`: identity and token lifecycle.
- `Payment Service`: owns the business transaction.
- `Account Service`: owns the money balance.
- `Notification Service`: sends side effects like email.
- `Kafka`: event backbone for transfer workflow.
- `Redis`: low-latency temporary state, not money truth.
- `MySQL`: source of truth.
- `Prometheus/Grafana`: operational visibility.
- `SonarQube`: code health, not runtime health.

### Why this architecture is good

Because each service owns one critical responsibility clearly:

- auth owns identity
- payment owns transfer workflow
- account owns balance mutation
- notification owns communication

That separation prevents the most dangerous mistake in banking systems: letting too many parts of the system mutate money state.

---

## 2. Explain Saga Happy Path In 6 Steps

### Short spoken version

> "The happy path is: first, payment-service accepts the transfer request, validates it, stores the transaction and an outbox event, and returns a pending response. Second, the outbox publisher sends the payment initiated event to Kafka. Third, account-service consumes that event and debits the source account. Fourth, payment-service receives the account debited event and emits the next event to request destination credit. Fifth, account-service credits the destination account and publishes account credited. Sixth, payment-service marks the transaction completed and notification-service sends the success email asynchronously." 

### The 6 steps with exact BankFlow mapping

1. `PaymentService.initiateTransfer()`
   - validates request
   - checks idempotency key in Redis
   - saves `Transaction`
   - saves `OutboxEvent(PAYMENT_INITIATED)`
   - returns `PENDING`

2. `OutboxPublisher.publishPendingEvents()`
   - reads pending outbox rows
   - publishes `PAYMENT_INITIATED` to Kafka
   - marks event published after broker acknowledgement

3. `AccountSagaConsumer` in account-service
   - consumes the initiated event
   - debits the source account
   - writes `AccountAuditLog`
   - publishes `ACCOUNT_DEBITED`

4. `PaymentSagaConsumer.handleAccountDebited()`
   - loads transaction
   - moves saga status to `ACCOUNT_DEBITED`
   - saves outbox event requesting destination credit

5. `AccountSagaConsumer` handles credit request
   - credits destination account
   - writes audit log
   - publishes `ACCOUNT_CREDITED`

6. `PaymentSagaConsumer.handleAccountCredited()`
   - sets transaction status to `COMPLETED`
   - sets saga status to `COMPLETED`
   - records `completedAt`
   - emits `PAYMENT_COMPLETED`
   - notification-service later sends email

### Why this is the happy path

Because both financial legs succeed:

- source debit succeeds
- destination credit succeeds

No compensation is needed.

### Why the interviewer cares

Because if you can explain the six steps cleanly, it proves you understand distributed workflow as a sequence of durable business state changes, not just as API calls.

---

## 3. Explain Saga Compensation In 4 Steps

### Short spoken version

> "Compensation starts when source debit succeeded but destination credit failed. First, payment-service receives account credit failed and moves the saga into compensating. Second, it publishes a compensation requested event to reverse the original debit. Third, account-service credits the source account back and publishes reversal completed. Fourth, payment-service marks the transaction reversed and the saga compensated." 

### The 4 steps with BankFlow mapping

1. `PaymentSagaConsumer.handleCreditFailed()`
   - source money already left the sender
   - destination did not receive it
   - payment-service sets saga status to `COMPENSATING`
   - saves `COMPENSATION_REQUESTED` in outbox

2. `OutboxPublisher` publishes the compensation event
   - event goes to Kafka
   - account-service receives the reversal instruction

3. `AccountSagaConsumer.handleCompensationRequested()`
   - credits the original source account back
   - writes reversal audit log
   - publishes `ACCOUNT_REVERSAL_COMPLETED`

4. `PaymentSagaConsumer.handleReversalCompleted()`
   - transaction status becomes `REVERSED`
   - saga status becomes `COMPENSATED`
   - emits `PAYMENT_REVERSED`

### The deep concept

Compensation is not a database rollback.

That is one of the most important things to say clearly.

Why?

Because once source debit has already happened in another service and maybe another transaction boundary, you cannot "undo" the world with a simple rollback. You need a new business action that semantically reverses the old one. That is what compensation means.

### Senior phrasing

> "A saga rollback is not infrastructure rollback. It is a business reversal modeled as a new state transition."

---

## 4. Explain Outbox Pattern In 2 Sentences

### Interview-safe 2 sentence answer

> "The outbox pattern solves the dual write problem, where saving business state to the database and publishing an event to Kafka are two separate operations that can succeed or fail independently. In BankFlow, payment-service stores the `Transaction` and `OutboxEvent` in the same database transaction, and a separate `OutboxPublisher` reliably publishes pending outbox rows later, so business state and event intent never drift apart." 

### Full explanation

### The problem

Naive code looks like this:

1. save transaction in DB
2. publish event to Kafka

That looks fine until failure happens.

Failure case A:

- DB save succeeds
- service crashes before Kafka publish
- transaction exists forever in pending state
- downstream services never hear about it

Failure case B:

- Kafka publish succeeds
- DB transaction fails or is rolled back
- downstream services act on an event for a transaction that does not exist durably

That is the dual write problem.

### The solution

Store both of these in the same local DB transaction:

- business row
- event row

Then publish the event later from the outbox table.

### Why this is powerful

Because databases are very good at making local transactions atomic.

So instead of trying to make MySQL and Kafka act like one atomic system, we reduce the problem:

- atomic DB commit first
- asynchronous reliable publish second

### BankFlow implementation

- `Transaction`
- `OutboxEvent`
- `PaymentTransactionWriter`
- `OutboxPublisher`

---

## 5. Explain Why BigDecimal, Not Double, For Money

### Short spoken answer

> "I never use `double` or `float` for money because binary floating-point cannot represent decimal fractions exactly. In banking, even tiny precision errors become unacceptable when repeated across many transactions, so BankFlow uses `BigDecimal` for balances and amounts to preserve exact decimal arithmetic." 

### The classic example

```java
System.out.println(0.1 + 0.2);
```

Output is not exactly `0.3`.

It becomes something like:

```text
0.30000000000000004
```

That happens because `double` stores values in binary floating-point format, and decimal fractions like `0.1` and `0.2` cannot be represented exactly in that format.

### Why this is dangerous in banking

A beginner may say:

- "the difference is tiny"

A banker or regulator says:

- "the difference is unacceptable"

Why?

Because money systems care about exactness, not approximate closeness.

If that error repeats across millions of transactions, you create reconciliation differences, audit failures, customer disputes, and compliance issues.

### Correct approach

```java
new BigDecimal("0.1").add(new BigDecimal("0.2"))
```

This gives exactly `0.3`.

### Why string constructor matters

Even with `BigDecimal`, you should prefer string initialization for exact decimals. If you do `new BigDecimal(0.1)`, you import the floating-point approximation before BigDecimal even gets a chance to help.

### Where BankFlow uses it

- `Account.balance`
- `Transaction.amount`
- `AccountAuditLog.previousBalance`
- `AccountAuditLog.newBalance`

### Senior phrasing

> "For money, approximate arithmetic is a correctness bug, not a performance trade-off."

---

## 6. Explain Optimistic Locking: What Race Condition, How `@Version` Fixes It

### Short spoken answer

> "Optimistic locking protects BankFlow from lost updates during concurrent balance changes. The `@Version` field on `Account` makes JPA reject a stale write if another transaction has already updated that row, so instead of silently overwriting the latest balance we retry with fresh data or fail correctly with insufficient funds." 

### The race condition without optimistic locking

Suppose balance is `1000`.

Two requests arrive almost together:

- Thread A wants to debit `600`
- Thread B wants to debit `800`

Without `@Version`:

1. Thread A reads balance `1000`
2. Thread B reads balance `1000`
3. Thread A writes `400`
4. Thread B still thinks balance is `1000`
5. Thread B writes `200`

Now both debits appear to have succeeded, but that is wrong.

What happened?

The second write overwrote the first one using stale state.

This is called a **lost update**.

### How `@Version` fixes it

With `@Version` on `Account`:

- first write changes version from, for example, `1` to `2`
- second write still carries version `1`
- JPA checks DB row version and sees mismatch
- JPA throws optimistic locking failure instead of accepting stale write

That is exactly what you want.

### Why retry matters

In BankFlow, `AccountCommandService.debitAccount()` is also `@Retryable`.

That means after conflict:

1. read the latest account again
2. reevaluate business rules on fresh state
3. either succeed or throw `InsufficientFundsException`

This is important because the correct answer is not just “retry blindly.”

The correct answer is “re-read and re-decide.”

### Proof in BankFlow tests

`AccountConcurrencyIT` is the strongest proof here.

That test fires ten concurrent debits and verifies the final balance is exactly correct.

### Senior phrasing

> "Optimistic locking is not about making concurrency disappear. It is about detecting stale decisions before they corrupt financial state." 

---

## 7. Explain Redis Caching: Why Different TTLs, What `TTL = remaining` Means

This topic actually contains two different ideas in BankFlow:

1. **read caching TTLs** in account-service
2. **remaining lifetime TTLs** in auth blacklist

A good answer should separate them clearly.

### 7.1 Different TTLs in Account-Service Caching

### Short spoken answer

> "BankFlow uses different Redis TTLs because not all data changes at the same speed or has the same correctness sensitivity. Account details can tolerate a longer cache window like 5 minutes, but balance changes much more frequently, so its cache TTL is only 30 seconds." 

### Why the account cache can be longer

`getAccountById()` returns details like:

- account number
- account type
- status
- metadata that changes infrequently

That is a good candidate for a longer TTL.

### Why balance cache is shorter

Balance changes more often and users care about freshness.

A stale balance shown in UI for a few seconds may be acceptable.

A stale balance used to authorize debit is not acceptable.

That is why BankFlow:

- uses cached balance for read optimization
- but command side always reads fresh DB state for actual debit/credit logic

### BankFlow mapping

- `AccountQueryService`
- `CacheConfig`
- `accounts` cache TTL = 5 minutes
- `balances` cache TTL = 30 seconds

### 7.2 What `TTL = remaining` Means In JWT Blacklisting

### Short spoken answer

> "For JWT blacklisting, the Redis key should live only as long as the token could still be used. So if the token has 7 minutes left before expiry, the blacklist entry gets TTL 7 minutes, not some fixed number like 24 hours." 

### Why this matters

Suppose token expires naturally in 10 minutes.

If you blacklist it with a fixed TTL of 24 hours:

- Redis keeps useless data much longer than needed
- memory fills with stale revoked-token entries

If you blacklist it with `TTL = remaining token lifetime`:

- blacklist key exists exactly while it matters
- after token naturally expires, key disappears automatically

### Why this is elegant

Because it aligns revocation lifetime with token validity lifetime.

No manual cleanup needed.

### BankFlow mapping

- `JwtService.blacklistToken()`
- `JwtService.isTokenBlacklisted()`
- Redis key uses `CacheKeys.BLACKLIST_PREFIX + jti`

### Senior phrasing

> "TTL should match the business validity window of the cached thing, not an arbitrary round number." 

---

## 8. Explain Circuit Breaker: 3 States, Transition Triggers

### Short spoken answer

> "A circuit breaker has three states: closed, open, and half-open. In closed state requests flow normally while failures are measured, in open state requests fail fast because the downstream is considered unhealthy, and in half-open state the system allows a few test requests to check whether the downstream recovered." 

### The three states deeply explained

### 1. CLOSED

This is the normal state.

Requests are allowed through.

At the same time, the circuit breaker records the results of recent calls in a sliding window.

In BankFlow gateway config, the breaker watches recent failures for routes like account-service and payment-service.

### Transition from CLOSED to OPEN

If failure rate crosses the threshold, for example 50 percent of the last 10 calls, the breaker trips.

That means:

- downstream is likely unhealthy
- continuing to hammer it is harmful
- we stop trying every request

### 2. OPEN

Requests are blocked immediately.

No network call is made to the downstream service.

Instead, fallback is returned immediately, such as a `503` from `FallbackController`.

This is important because failing fast is often healthier than waiting on timeouts repeatedly.

### Transition from OPEN to HALF-OPEN

After a wait period, like 30 seconds, the breaker gives the downstream a chance to prove recovery.

### 3. HALF-OPEN

Only a small number of trial requests are allowed through.

If those succeed:

- downstream is considered healthy again
- breaker returns to CLOSED

If they fail:

- breaker goes back to OPEN
- another cooldown period starts

### Why interviewers ask this

Because circuit breaker is not just “we return 503 when service is down.”

It is a control loop that protects the system from cascading failures.

### BankFlow mapping

- API Gateway route config
- Resilience4j configuration in gateway `application.yml`
- `FallbackController`

### Senior phrasing

> "Circuit breaker is not only an availability feature. It is a blast-radius control feature." 

---

## 9. Explain Rate Limiting: Token Bucket Algorithm In 3 Sentences

### Interview-safe 3 sentence answer

> "BankFlow uses Redis-backed token bucket rate limiting at the API Gateway. A bucket fills at a configured replenish rate up to a maximum burst capacity, and every request consumes one token. If the bucket is empty the request is rejected with 429, which allows short bursts of normal traffic but blocks sustained abuse." 

### Deeper explanation

Imagine a bucket that can hold 20 tokens.

- `burstCapacity = 20`
- `replenishRate = 20 per second`

That means:

- user can make 20 requests quickly and all succeed
- request 21 in the same instant is rejected
- after one second, 20 tokens are added back

### Why token bucket is better than a naive fixed window

A fixed window can be unfair at boundaries.

Example:

- 20 requests at `12:00:00.999`
- 20 requests again at `12:00:01.001`

A fixed window may allow both bursts, effectively permitting 40 requests in a tiny interval.

Token bucket handles bursts more smoothly.

### Why this matters in banking

You want to allow real human bursts:

- app refreshes
- quick retries
- multiple dashboard calls

But you do not want:

- brute-force login flood
- transfer spam
- bot abuse

### BankFlow mapping

- API Gateway `RequestRateLimiter`
- `GatewayConfig.userKeyResolver()`
- Redis rate limiter configuration in gateway `application.yml`

---

## 10. Explain JWT Blacklisting: Why `jti`, Why Redis, Why `TTL = remaining`

### Short spoken answer

> "JWTs are stateless, so once issued they normally remain valid until expiry. BankFlow adds a unique `jti` claim to every token, stores revoked token IDs in Redis, and sets the Redis TTL equal to the token’s remaining lifetime so logout and emergency revocation work immediately without keeping stale blacklist entries forever." 

### Why `jti` matters

`jti` means JWT ID.

It is a unique token identifier.

Without `jti`, if you want to revoke a token you only know:

- username
- maybe roles
- maybe issued time

But you do not have a clean unique ID for that exact token instance.

With `jti`:

- each access token becomes individually addressable
- logout can target one specific token
- Redis can store exactly that revoked token ID

### Why Redis is used

Because token validation happens on almost every authenticated request.

That means blacklist checking must be:

- very fast
- very simple
- TTL-friendly

Redis is perfect for that.

Using MySQL for every request would be slower and more expensive operationally.

### Why `TTL = remaining`

Suppose a token expires naturally in 12 minutes.

If you revoke it now, the blacklist entry only needs to exist for 12 minutes.

After that, even without blacklist the token would already be expired.

So `TTL = remaining lifetime` is the most efficient and correct policy.

### BankFlow mapping

- `JwtService.generateAccessToken()` adds `jti`
- `JwtService.blacklistToken()` stores Redis key using `jti`
- `JwtService.isTokenBlacklisted()` checks Redis before accepting token
- gateway also validates revoked tokens through JWT service logic

### Senior phrasing

> "Redis is not being used here as a source of truth. It is being used as a fast revocation index over otherwise stateless tokens." 

---

## 11. Answer: "Kafka Goes Down Mid-Transfer. What Happens?"

### Strong short answer

> "In BankFlow, Kafka unavailability should delay transfer progression, not lose the transfer. Payment-service first writes the `Transaction` and matching `OutboxEvent` in the same MySQL transaction, so if Kafka is down the event remains pending in the outbox and the publisher retries later. The transfer may stay in `PENDING` longer, but money and workflow state remain durable and recoverable." 

### Full answer with cases

This question can mean different things, so a strong answer should break it down.

### Case 1: Kafka is down when payment-service tries to publish initial event

In BankFlow:

- `PaymentService.initiateTransfer()` still saves:
  - `Transaction`
  - `OutboxEvent(PAYMENT_INITIATED)`
- both are in one DB transaction
- `OutboxPublisher` cannot publish to Kafka right now
- event stays pending or increments retry count

Result:

- transfer intent is not lost
- no debit happened yet
- user may see pending status longer

This is a delay problem, not a corruption problem.

### Case 2: Kafka goes down after source debit but before next saga event is published

This is exactly why durable state transitions matter.

The system must not rely on in-memory event publication as the only record that something happened. If the service persists its business state and event intent durably, then after restart it can continue or reconcile from stored state.

In BankFlow’s design language, the important principle is:

- never let external publish be the only proof that a financial step happened

### What does not happen

A strong answer should explicitly say what is prevented:

- the transfer is not silently forgotten
- the user is not charged twice just because Kafka retries later
- the data is not lost in memory

### What the user experiences

Possible outcomes:

- request accepted as pending
- completion delayed
- UI shows processing state longer than usual

That is acceptable if the system preserves correctness.

### What operations team should do

Senior answer includes operations view too:

- alert on outbox backlog growth
- alert on Kafka publish failures
- monitor pending transaction age
- if backlog grows too large, activate incident response

### Why this is a strong answer

Because it shows you understand that reliable financial systems degrade into slower progress, not silent loss.

---

## 12. Final Rapid Revision Lines

If you need last-minute interview revision, memorize these one-liners.

### Architecture

> "Gateway protects the edge, payment owns workflow, account owns balances, Kafka carries state changes, Redis handles low-latency temporary state, and MySQL holds financial truth."

### Saga happy path

> "Initiate, outbox publish, source debit, debit-confirmed, destination credit, completed."

### Compensation

> "Credit failed, compensation requested, source reversal, transaction compensated."

### Outbox

> "Outbox solves the DB-plus-Kafka dual write problem by storing event intent atomically with business state, then publishing asynchronously."

### BigDecimal

> "Money needs exact decimal arithmetic, not approximate binary floating-point."

### Optimistic locking

> "`@Version` detects stale writes so concurrent debit decisions cannot silently overwrite each other."

### Redis caching

> "TTL follows freshness needs: longer for stable account data, shorter for balance, and remaining lifetime for token blacklist."

### Circuit breaker

> "Closed observes, open blocks, half-open tests recovery."

### Rate limiting

> "Token bucket allows small bursts but blocks sustained abuse."

### JWT blacklist

> "`jti` identifies one exact token, Redis stores revoked IDs fast, and TTL matches the token’s remaining life."

### Kafka down mid-transfer

> "Outbox means Kafka outage delays progress but does not lose transfer intent."

---

## 13. The Most Important Meta-Lesson

If an interviewer asks any of these questions, do not answer like you memorized a definition.

Answer in this order:

1. what problem exists
2. why it is dangerous in banking
3. how BankFlow solves it
4. what trade-off remains

That structure makes even a simple answer sound like it comes from someone who has actually built the system.
