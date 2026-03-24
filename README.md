# BankFlow

BankFlow is a production-grade distributed banking platform for local development and learning. The repository is organized as a multi-module Spring Boot workspace with shared infrastructure, independently deployable services, API gateway routing, local observability, and event-driven communication over Kafka.

## Overview

BankFlow models a typical modern banking backend split into bounded contexts:

- `auth-service` handles user identity, login, tokens, OTP, and security flows.
- `account-service` manages customer accounts, balances, and account-level operations.
- `payment-service` orchestrates money movement and long-running payment workflows.
- `notification-service` delivers email-based customer notifications and consumes domain events.
- `api-gateway` is the single entry point for clients and centralizes routing and edge concerns.

```text
                       +----------------------+
                       |      API Clients     |
                       | Web, Mobile, Postman |
                       +----------+-----------+
                                  |
                                  v
                      +-------------------------+
                      |       API Gateway       |
                      |  Routing / Auth / Edge  |
                      +-----+------+-----+------+
                            |      |     |
            +---------------+      |     +----------------+
            |                      |                      |
            v                      v                      v
  +------------------+   +------------------+   +----------------------+
  |   Auth Service   |   |  Account Service |   |   Payment Service    |
  | users / tokens   |   | balances / acct  |   | transfers / saga     |
  +--------+---------+   +--------+---------+   +----------+-----------+
           |                      |                        |
           |                      |                        |
           +----------+-----------+------------+-----------+
                      |                        |
                      v                        v
               +-------------+         +---------------+
               |   MySQL     |         |     Kafka     |
               | per-service |<------->| domain events |
               | schemas     |         +-------+-------+
               +------+------+                 |
                      |                        v
                      |              +----------------------+
                      |              | Notification Service |
                      |              | email consumers      |
                      |              +----------+-----------+
                      |                         |
                      |                         v
                      |                    +---------+
                      |                    | MailHog |
                      |                    +---------+
                      |
                      v
                  +--------+
                  | Redis  |
                  | cache  |
                  +--------+

Observability: Prometheus -> Grafana
Quality: SonarQube -> PostgreSQL
```

## Prerequisites

- Git: https://git-scm.com/downloads
- Docker Desktop: https://www.docker.com/products/docker-desktop/
- JDK 21: https://adoptium.net/
- Apache Maven 3.9+: https://maven.apache.org/download.cgi
- IntelliJ IDEA: https://www.jetbrains.com/idea/download/
- Postman: https://www.postman.com/downloads/

## Start The Entire Project In 3 Commands

Mac/Linux:

```bash
./start-infra.sh
mvn -f bankflow-parent/pom.xml clean install
docker compose -f docker-compose.services.yml up --build -d
```

Windows:

```bat
start-infra.bat
mvn -f bankflow-parent\pom.xml clean install
docker compose -f docker-compose.services.yml up --build -d
```

## Access URLs

| Tool | URL | Default Login | Purpose |
|---|---|---|---|
| Kafka UI | http://localhost:8090 | None | Browse Kafka topics, partitions, and messages |
| Redis Commander | http://localhost:8091 | None | Inspect Redis keys and cached state |
| MailHog | http://localhost:8025 | None | View intercepted local emails |
| Prometheus | http://localhost:9090 | None | Inspect service metrics and scrape targets |
| Grafana | http://localhost:3000 | `admin / bankflow_grafana` | View dashboards and metrics |
| SonarQube | http://localhost:9000 | `admin / admin` | Static analysis and quality gates |

## Run Individual Services In IntelliJ

1. Import [bankflow-parent/pom.xml](d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/pom.xml) as a Maven project.
2. Start infrastructure first with [start-infra.bat](d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/start-infra.bat) or [start-infra.sh](d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/start-infra.sh).
3. Open a module such as `bankflow-auth-service` and run its main Spring Boot application class.
4. Set service ports to match this repo:
   `api-gateway=8080`, `auth-service=8081`, `account-service=8082`, `payment-service=8083`, `notification-service=8084`.
5. Use environment variables from `.env` or configure them in IntelliJ Run/Debug configurations.
6. Run multiple services in parallel by creating one IntelliJ configuration per service.

## Troubleshooting

### Docker memory on Windows and Mac

- Kafka, SonarQube, and Grafana are the biggest memory consumers.
- Set Docker Desktop memory to at least 6 GB for a smoother local setup.
- On Windows with WSL2, restart Docker Desktop if containers remain stuck in `starting`.
- On Mac, increase CPU and memory in Docker Desktop if Kafka or SonarQube enters a restart loop.

### Port conflicts

- BankFlow expects these host ports: `3000`, `3306`, `6379`, `8025`, `8080-8084`, `8090`, `8091`, `9000`, `9090`, `1025`.
- If a port is already in use, stop the existing process or change the compose port mapping.
- Common conflicts are local MySQL on `3306`, Redis on `6379`, Grafana on `3000`, and Prometheus on `9090`.

### Kafka not connecting

- Start infrastructure before starting services.
- Use `localhost:9092` when a Spring Boot service runs on the host.
- Use `bankflow-kafka:29092` only from containers on `bankflow-network`.
- If Kafka UI opens but shows no cluster, restart Docker Desktop and then rerun `start-infra`.

## Repository Layout

- [docker-compose.infrastructure.yml](d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/docker-compose.infrastructure.yml): Shared local infrastructure stack.
- [docker-compose.services.yml](d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/docker-compose.services.yml): Spring Boot service stack.
- [bankflow-parent](d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent): Maven parent and all service modules.
- [config](d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/config): MySQL, Prometheus, and Grafana provisioning.
- [docs](d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/docs): Architecture and local setup documentation.
- [postman](d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/postman): API collection placeholders.
