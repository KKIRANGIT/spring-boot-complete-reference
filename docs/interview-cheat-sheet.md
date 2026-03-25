# BankFlow Interview Cheat Sheet

Fast revision notes for the most important architecture patterns implemented in BankFlow.

## 1. Saga Choreography

- Use when one business transaction spans multiple microservices.
- BankFlow flow:
  `PaymentService.initiateTransfer(...)` -> `PaymentSagaConsumer.handleAccountDebited(...)` -> credit request -> `handleCreditFailed(...)` -> compensation -> `handleReversalCompleted(...)`.
- Key classes:
  [PaymentService.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-payment-service/src/main/java/com/bankflow/payment/service/PaymentService.java)
  [PaymentSagaConsumer.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-payment-service/src/main/java/com/bankflow/payment/messaging/PaymentSagaConsumer.java)
  [PaymentSagaService.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-payment-service/src/main/java/com/bankflow/payment/service/PaymentSagaService.java)
- Best line:
  “I used choreography so each service reacts to domain events without a central coordinator.”
- Trade-off:
  less coupling, more debugging complexity.

## 2. Outbox Pattern

- Solves the dual write problem between DB and Kafka.
- BankFlow writes `Transaction` and `OutboxEvent` in the same DB transaction.
- `OutboxPublisher` later publishes and marks the row `PUBLISHED`.
- Key classes:
  [OutboxEvent.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-payment-service/src/main/java/com/bankflow/payment/entity/OutboxEvent.java)
  [PaymentTransactionWriter.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-payment-service/src/main/java/com/bankflow/payment/service/PaymentTransactionWriter.java)
  [OutboxPublisher.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-payment-service/src/main/java/com/bankflow/payment/service/OutboxPublisher.java)
- Best line:
  “The database commit is the source of truth, and Kafka delivery becomes retriable.”
- Trade-off:
  more tables and scheduler logic, but consistent cross-system writes.

## 3. Idempotency

- Needed because clients retry and Kafka can redeliver.
- API layer:
  [PaymentService.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-payment-service/src/main/java/com/bankflow/payment/service/PaymentService.java)
  checks Redis key and returns the exact cached `TransferResponse`.
- Consumer layer:
  [NotificationIdempotencyService.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-notification-service/src/main/java/com/bankflow/notification/service/NotificationIdempotencyService.java)
- TTLs:
  24h for payment request reuse, 48h for notification dedupe.
- Best line:
  “I used Redis for the fast path and DB uniqueness for the hard safety boundary.”
- Trade-off:
  safer behavior, but more state to manage.

## 4. Optimistic Locking

- Protects account balance from lost updates.
- BankFlow uses `@Version` on:
  [Account.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-account-service/src/main/java/com/bankflow/account/entity/Account.java)
- Retries happen in:
  [AccountCommandService.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-account-service/src/main/java/com/bankflow/account/service/AccountCommandService.java)
- Proof test:
  [AccountConcurrencyIT.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-account-service/src/test/java/com/bankflow/account/service/AccountConcurrencyIT.java)
- Best line:
  “Without `@Version`, concurrent debits can silently overwrite each other and lose money.”
- Trade-off:
  strong correctness when conflicts are rare, retries under contention.

## 5. CQRS

- Separate write model from read model.
- Write side:
  [AccountCommandService.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-account-service/src/main/java/com/bankflow/account/service/AccountCommandService.java)
- Read side:
  [AccountQueryService.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-account-service/src/main/java/com/bankflow/account/service/AccountQueryService.java)
- Why:
  writes need fresh DB state; reads can use Redis.
- Best line:
  “I separated correctness-sensitive balance mutations from performance-optimized reads.”
- Trade-off:
  clearer responsibilities, but more classes and coordination.

## 6. Redis Caching Strategy

- Account details cache: 5 minutes.
- Balance cache: 30 seconds.
- Config:
  [CacheConfig.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-account-service/src/main/java/com/bankflow/account/config/CacheConfig.java)
- Query path:
  [AccountQueryService.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-account-service/src/main/java/com/bankflow/account/service/AccountQueryService.java)
- Invalidation on write:
  [AccountCommandService.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-account-service/src/main/java/com/bankflow/account/service/AccountCommandService.java)
- Best line:
  “I allow short-lived staleness for display reads, but never for actual debit decisions.”
- Trade-off:
  lower DB pressure, but freshness must be designed carefully.

## 7. Kafka, Consumer Groups, and DLQ

- Async event backbone for payment/account/notification flow.
- Manual ack mode:
  `MANUAL_IMMEDIATE`
- DLQ config:
  `DefaultErrorHandler` + `DeadLetterPublishingRecoverer`
- Key class:
  [KafkaConsumerConfig.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-notification-service/src/main/java/com/bankflow/notification/config/KafkaConsumerConfig.java)
- Consumer:
  [NotificationKafkaConsumer.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-notification-service/src/main/java/com/bankflow/notification/messaging/NotificationKafkaConsumer.java)
- Best line:
  “I acknowledge only after processing, so crash-before-ack gives redelivery instead of message loss.”
- Trade-off:
  at-least-once delivery, but duplicate handling is required.

## 8. JWT Security and Token Lifecycle

- Access token contains `jti`, `userId`, roles, email.
- Token blacklist uses Redis key by `jti`.
- TTL equals remaining token life.
- Refresh token rotation is in:
  [AuthService.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-auth-service/src/main/java/com/bankflow/auth/service/AuthService.java)
- JWT generation and blacklist:
  [JwtService.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-auth-service/src/main/java/com/bankflow/auth/service/JwtService.java)
- Best line:
  “JWT is stateless for speed, but `jti` plus Redis gives me targeted revocation.”
- Trade-off:
  fast auth, but every secured request may need Redis validation.

## 9. Circuit Breaker

- Configured in gateway route definitions and `resilience4j` section.
- File:
  [application.yml](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-api-gateway/src/main/resources/application.yml)
- Fallback responses:
  [FallbackController.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-api-gateway/src/main/java/com/bankflow/gateway/controller/FallbackController.java)
- State summary:
  CLOSED = normal, OPEN = fail fast, HALF-OPEN = probe recovery.
- Best line:
  “When payment-service is unhealthy, the gateway rejects fast instead of amplifying the outage.”
- Trade-off:
  protects the system, but intentionally sacrifices short-term availability for stability.

## 10. Rate Limiting

- Algorithm:
  token bucket via Spring Cloud Gateway RedisRateLimiter.
- Config:
  [application.yml](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-api-gateway/src/main/resources/application.yml)
- Key resolver:
  [GatewayConfig.java](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-api-gateway/src/main/java/com/bankflow/gateway/config/GatewayConfig.java)
- Logic:
  authenticated users limited by `X-User-Id`, public traffic by IP.
- Best line:
  “Token bucket allows legitimate bursts but blocks sustained abuse.”
- Trade-off:
  simple and effective, but unfairness can appear behind shared IPs.

## 30-Second Project Summary

“BankFlow is a distributed banking platform where correctness is protected at multiple layers. I used saga choreography plus outbox for cross-service consistency, optimistic locking for concurrent balance safety, idempotency to stop duplicate transfers, CQRS and Redis caching for scalable reads, Kafka with manual ack and DLQ for resilient async processing, JWT plus refresh rotation for secure sessions, and gateway-level circuit breaker and rate limiting to protect the platform boundary.”

## Golden Rule For Answers

Use this structure in interviews:

1. Define the pattern in one line.
2. Point to the BankFlow class that implements it.
3. Explain the failure mode it prevents.
4. End with the trade-off.
