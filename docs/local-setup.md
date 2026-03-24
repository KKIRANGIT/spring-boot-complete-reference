# Local Quick Start

## Step 1
Clone the project and open [bankflow-parent/pom.xml](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/pom.xml) in IntelliJ IDEA as the Maven root.

## Step 2
Start infrastructure:

```bash
docker compose -f docker-compose.infrastructure.yml up -d
```

## Step 3
Wait until infrastructure is healthy:

```bash
docker ps --format "table {{.Names}}\t{{.Status}}"
```

Expected healthy infra containers:
- `bankflow-mysql`
- `bankflow-redis`
- `bankflow-kafka`
- `bankflow-mailhog`
- `bankflow-prometheus`
- `bankflow-grafana`

## Step 4
Create IntelliJ run configurations for each Spring Boot service.

Common steps for every service:
1. In IntelliJ, open `Run > Edit Configurations`.
2. Click `+` and choose `Spring Boot`.
3. Set `VM options` to `-Dspring.profiles.active=local`.
4. Add environment variables:
   `MYSQL_ROOT_PASSWORD=bankflow_root;REDIS_PASSWORD=bankflow_redis;JWT_SECRET=bankflow-local-secret-must-be-32chars`
5. Apply and repeat for the next service.

Per-service values:
- Auth Service
  Main class: `com.bankflow.auth.BankflowAuthServiceApplication`
  Use module: `bankflow-auth-service`
- Account Service
  Main class: `com.bankflow.account.BankflowAccountServiceApplication`
  Use module: `bankflow-account-service`
- Payment Service
  Main class: `com.bankflow.payment.BankflowPaymentServiceApplication`
  Use module: `bankflow-payment-service`
- Notification Service
  Main class: `com.bankflow.notification.BankflowNotificationServiceApplication`
  Use module: `bankflow-notification-service`
  Optional extra env vars: `MAIL_HOST=localhost;MAIL_PORT=1025`
- API Gateway
  Main class: `com.bankflow.gateway.BankflowApiGatewayApplication`
  Use module: `bankflow-api-gateway`

## Step 5
Import both Postman files:
- [BankFlow.postman_collection.json](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/postman/BankFlow.postman_collection.json)
- [BankFlow.local.postman_environment.json](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/postman/BankFlow.local.postman_environment.json)

## Step 6
Run the basic flow in order:
1. `Register User`
2. `Login`
3. `Create Account`
4. Create a second account
5. `Transfer Payment`

## Step 7
Open Kafka UI at `http://localhost:8090` and verify the topic `bankflow.payment.initiated` contains the new transfer event.

## Step 8
Open MailHog at `http://localhost:8025` and verify the notification email appears there.

## Step 9
Open Grafana at `http://localhost:3000` and confirm the service metrics are visible.

## Optional Docker-only Run
If you want all five services to run as containers instead of IntelliJ processes, use the merged compose model so `depends_on: condition: service_healthy` can reference the infra services:

```bash
docker compose -f docker-compose.infrastructure.yml -f docker-compose.services.yml up -d --build
```
