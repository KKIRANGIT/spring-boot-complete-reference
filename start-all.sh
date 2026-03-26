#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "${SCRIPT_DIR}"

CONTAINERS=(
  "bankflow-mysql"
  "bankflow-redis"
  "bankflow-kafka"
  "bankflow-mailhog"
  "bankflow-prometheus"
  "bankflow-grafana"
  "bankflow-sonar-db"
  "bankflow-sonarqube"
  "bankflow-auth-service"
  "bankflow-account-service"
  "bankflow-payment-service"
  "bankflow-notification-service"
  "bankflow-api-gateway"
  "bankflow-kafka-ui"
  "bankflow-redis-ui"
)

docker compose \
  -f docker-compose.infrastructure.yml \
  -f docker-compose.services.yml \
  up -d --build

echo "Waiting for BankFlow services to be healthy..."

for container in "${CONTAINERS[@]}"; do
  printf "  - %s" "${container}"
  until status="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "${container}" 2>/dev/null)" \
    && [[ "${status}" == "healthy" || "${status}" == "running" ]]; do
    printf "."
    sleep 5
  done
  echo " healthy"
done

cat <<'EOF'

BankFlow full stack is ready.

Gateway:         http://localhost:8080
Gateway Swagger: http://localhost:8080/swagger-ui/index.html
Auth Swagger:    http://localhost:8081/swagger-ui/index.html
Account Swagger: http://localhost:8082/swagger-ui/index.html
Payment Swagger: http://localhost:8083/swagger-ui/index.html
Notify Swagger:  http://localhost:8084/swagger-ui/index.html
Kafka UI:        http://localhost:8090
Redis Commander: http://localhost:8091
MailHog UI:      http://localhost:8025
Prometheus:      http://localhost:9090
Grafana:         http://localhost:3000   (admin / bankflow_grafana)
SonarQube:       http://localhost:9000   (admin / admin)
EOF
