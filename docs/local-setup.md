# Local Setup

## Startup Order

1. Start infrastructure with `start-infra.sh` or `start-infra.bat`.
2. Build all modules with `mvn -f bankflow-parent/pom.xml clean install`.
3. Start services from IntelliJ or with `docker compose -f docker-compose.services.yml up --build -d`.

## Expected Service Ports

- API Gateway: `8080`
- Auth Service: `8081`
- Account Service: `8082`
- Payment Service: `8083`
- Notification Service: `8084`

## Expected Infrastructure Ports

- MySQL: `3306`
- Redis: `6379`
- Kafka external listener: `9092`
- Kafka UI: `8090`
- Redis Commander: `8091`
- MailHog SMTP: `1025`
- MailHog UI: `8025`
- Prometheus: `9090`
- Grafana: `3000`
- SonarQube: `9000`

## Environment Notes

- Keep real credentials in `.env`.
- Commit only `.env.example`.
- Host-run Spring Boot apps should use `localhost:9092` for Kafka.
- Container-run Spring Boot apps should use `bankflow-kafka:29092`.

## Recommended IntelliJ Workflow

- Import the parent Maven project.
- Create one Run/Debug configuration per microservice.
- Start only the services you are actively working on.
- Keep Prometheus and MailHog running so metrics and emails are visible during development.
