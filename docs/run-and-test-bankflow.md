# Run And Test BankFlow Locally

This guide is the exact local startup sequence for BankFlow.

It is written to help you do two things safely:

1. start the whole application in the correct order
2. verify that every major part is actually working

---

## 1. Pre-check Before You Start

Make sure these are installed and available on your machine:

- Docker Desktop
- Java 17
- Maven
- IntelliJ IDEA, if you want to run services from the IDE

Make sure these ports are free:

- `8080` API Gateway
- `8081` Auth Service
- `8082` Account Service
- `8083` Payment Service
- `8084` Notification Service
- `3000` Grafana
- `3306` MySQL
- `6379` Redis
- `8025` MailHog UI
- `8090` Kafka UI
- `8091` Redis Commander
- `9000` SonarQube
- `9090` Prometheus

Your repo root should be:

- [spring-boot-complete-reference](d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference)

Also make sure the local env file exists:

- [.env](d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/.env)

Minimum required values in `.env`:

```env
MYSQL_ROOT_PASSWORD=bankflow_root
REDIS_PASSWORD=bankflow_redis
JWT_SECRET=bankflow-local-jwt-secret-must-be-32-chars-min
```

Why this matters:

- MySQL will not start correctly for your services without DB credentials
- Redis-backed blacklist and idempotency checks depend on the Redis password
- JWT validation between auth-service and gateway depends on the same shared secret

---

## 2. Start Infrastructure First

Run this from the repo root:

```powershell
docker compose -f docker-compose.infrastructure.yml up -d
```

Then check status:

```powershell
docker compose -f docker-compose.infrastructure.yml ps
```

Wait until these services are healthy:

- `bankflow-mysql`
- `bankflow-redis`
- `bankflow-kafka`
- `bankflow-mailhog`
- `bankflow-prometheus`
- `bankflow-grafana`
- `bankflow-sonarqube`

Why infrastructure comes first:

Because your Spring Boot services depend on it at startup.

Examples:

- auth-service needs MySQL and Redis
- account-service needs MySQL, Redis, and Kafka
- payment-service needs MySQL, Redis, and Kafka
- notification-service needs MySQL, Redis, Kafka, and MailHog
- gateway needs Redis

If you start services before infra is ready, you may see misleading startup failures that are not actually code problems.

---

## 3. Build The Whole Project Once

Run this from the repo root:

```powershell
mvn.cmd --% -Dmaven.repo.local=.m2/repository -f bankflow-parent/pom.xml clean install -DskipTests
```

Why this step is important:

- it downloads all required dependencies locally
- it ensures your recent OpenAPI changes are compiled
- it builds all modules consistently before runtime testing

This is especially important for the gateway because Swagger UI depends on the Springdoc WebFlux dependency being present locally.

If you skip this step, a service may fail just because dependencies were never resolved on your machine after the latest changes.

---

## 4. Start The Five Services

Recommended approach:

Run the five Spring Boot services from IntelliJ, not from Docker.

Why this is the best local developer setup:

- faster debugging
- easier log reading
- easier breakpoint use
- easier to isolate one failing service

### IntelliJ Run Configuration For Each Service

In IntelliJ:

1. Go to `Run > Edit Configurations`
2. Click `+`
3. Choose `Spring Boot`
4. Create one configuration per service

### Main Classes

Use these exact main classes:

- Auth: `com.bankflow.auth.BankflowAuthServiceApplication`
- Account: `com.bankflow.account.BankflowAccountServiceApplication`
- Payment: `com.bankflow.payment.BankflowPaymentServiceApplication`
- Notification: `com.bankflow.notification.BankflowNotificationServiceApplication`
- Gateway: `com.bankflow.gateway.BankflowApiGatewayApplication`

### VM Options For Each

```text
-Dspring.profiles.active=local
```

### Environment Variables For Each

```text
MYSQL_ROOT_PASSWORD=bankflow_root
REDIS_PASSWORD=bankflow_redis
JWT_SECRET=bankflow-local-jwt-secret-must-be-32-chars-min
```

### Start Order

Start them in this order:

1. Auth Service
2. Account Service
3. Payment Service
4. Notification Service
5. API Gateway

Why this order works well:

- Auth and account are foundational
- payment depends heavily on account and Kafka
- notification depends on Kafka and business events
- gateway should start last because it fronts everything else

### Terminal Alternative

If you do not want IntelliJ, you can run each service from its module folder using:

```powershell
mvn spring-boot:run
```

But use separate terminals for separate services.

---

## 5. Verify That Each Service Actually Started

After startup, open these URLs:

### Swagger URLs

- Gateway Swagger: `http://localhost:8080/swagger-ui/index.html`
- Auth Swagger: `http://localhost:8081/swagger-ui/index.html`
- Account Swagger: `http://localhost:8082/swagger-ui/index.html`
- Payment Swagger: `http://localhost:8083/swagger-ui/index.html`
- Notification Swagger: `http://localhost:8084/swagger-ui/index.html`

Important note about notification-service:

It is event-driven, so Swagger mainly shows service metadata and operational surface, not a normal set of business REST endpoints.

### Infrastructure UI URLs

- Kafka UI: `http://localhost:8090`
- Redis Commander: `http://localhost:8091`
- MailHog: `http://localhost:8025`
- Prometheus: `http://localhost:9090`
- Grafana: `http://localhost:3000`
- SonarQube: `http://localhost:9000`

If these pages load, that tells you the platform is at least alive.

If the application behavior is still wrong, then the problem is deeper than startup.

---

## 6. Functional Test Flow

The cleanest way to test the application end to end is with Postman.

Import these two files:

- [BankFlow.postman_collection.json](d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/postman/BankFlow.postman_collection.json)
- [BankFlow.local.postman_environment.json](d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/postman/BankFlow.local.postman_environment.json)

Then run requests in this order:

1. `Register`
2. `Login`
3. `Create Account`
4. `Create Second Account`
5. `Transfer`

### What you should expect

After `Register`:

- user should be created in auth-service

After `Login`:

- access token should be stored in Postman environment
- refresh token should also be stored

After `Create Account`:

- account ID should be returned
- account should exist in account-service

After `Transfer`:

- payment request should return accepted or pending
- payment-service should create a transaction
- account-service should debit and credit as saga progresses
- Kafka should contain related events
- notification-service should eventually send email

---

## 7. What To Verify In Supporting Tools

### Kafka UI

Open:

- `http://localhost:8090`

Check for topics like:

- `bankflow.payment.initiated`
- `bankflow.account.debited`
- `bankflow.account.credited`
- `bankflow.payment.completed`
- `bankflow.payment.failed`
- `bankflow.payment.reversed`

Why this matters:

Kafka UI lets you confirm the saga is actually progressing through events, not just returning a local HTTP response.

### MailHog

Open:

- `http://localhost:8025`

You should see notification emails after successful or reversed transfers.

Why this matters:

It proves notification-service consumed the event and rendered the email.

### Grafana

Open:

- `http://localhost:3000`

Login with:

- username: `admin`
- password: `bankflow_grafana`

You should see service metrics once traffic starts flowing.

Why this matters:

A healthy system should not only work. It should also be observable.

### Prometheus

Open:

- `http://localhost:9090`

Check targets and metrics scraping.

This confirms observability pipeline health.

---

## 8. If `http://localhost:8080/swagger-ui/index.html` Does Not Work

This is the most common question during local setup.

Check these in order.

### Step 1: Is the gateway actually running?

Run:

```powershell
netstat -ano | findstr :8080
```

If nothing is listening on port `8080`, the gateway is not up.

### Step 2: Did the gateway crash on startup?

Check the IntelliJ console or terminal logs for `bankflow-api-gateway`.

Typical causes:

- missing Redis
- wrong JWT secret
- missing Maven dependency resolution after project changes
- port already in use

### Step 3: Did you build after the OpenAPI dependency change?

Run again if needed:

```powershell
mvn.cmd --% -Dmaven.repo.local=.m2/repository -f bankflow-parent/pom.xml clean install -DskipTests
```

Why this matters:

Swagger UI in gateway depends on the new WebFlux Springdoc dependency being available locally.

### Step 4: Are you running the correct gateway main class?

It must be:

- `com.bankflow.gateway.BankflowApiGatewayApplication`

If you start the wrong module, the Swagger URL will never work.

---

## 9. If Something Fails, Diagnose In This Order

Use this order instead of guessing.

1. Is infrastructure healthy?
2. Is the service running on the expected port?
3. Did the service fail on boot?
4. Is the database reachable?
5. Is Redis reachable?
6. Is Kafka reachable?
7. Does the gateway route correctly?
8. Does Postman send the expected headers and token?

This order is important because local debugging gets messy when you jump between layers randomly.

---

## 10. Recommended First Live Test

If you want a minimal test before trying the whole platform, do this:

1. Start infrastructure
2. Start `auth-service`
3. Start `api-gateway`
4. Open `http://localhost:8080/swagger-ui/index.html`

If that works, then your gateway Swagger path is confirmed.

After that, expand to:

1. account-service
2. payment-service
3. notification-service
4. Postman end-to-end transfer flow

This staged approach is better than trying to debug all five services at once.

---

## 11. Practical Advice While Testing

- Do not test everything from browser tabs only. Use Postman for real API flow.
- Do not trust only HTTP 200 or 202. Also verify Kafka UI, MailHog, and database effects.
- If transfer returns accepted but nothing completes, inspect Kafka and payment outbox logic next.
- If login works but gateway requests fail, check JWT secret consistency between auth-service and gateway.
- If account reads work but debits fail unexpectedly, inspect account-service logs and optimistic locking behavior.

---

## 12. Short Checklist

Use this as your final startup checklist.

- [ ] `.env` exists and has valid local secrets
- [ ] Docker Desktop is running
- [ ] Infrastructure stack is healthy
- [ ] `mvn clean install -DskipTests` completed successfully
- [ ] Auth service started on `8081`
- [ ] Account service started on `8082`
- [ ] Payment service started on `8083`
- [ ] Notification service started on `8084`
- [ ] Gateway started on `8080`
- [ ] Gateway Swagger opens
- [ ] Postman login works
- [ ] Account creation works
- [ ] Transfer request works
- [ ] Kafka events are visible
- [ ] MailHog shows notification mail
- [ ] Grafana/Prometheus show metrics

---

## 13. Best Next Step If You Are Stuck

If any step fails, do not try to fix five things at once.

Bring the system up in this exact order:

1. infrastructure
2. auth-service
3. gateway
4. gateway Swagger
5. account-service
6. payment-service
7. notification-service
8. Postman end-to-end test

That isolates the failure much faster.
