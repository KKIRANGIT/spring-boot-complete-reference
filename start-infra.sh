#!/usr/bin/env bash

set -euo pipefail

COMPOSE_FILE="docker-compose.infrastructure.yml"
CONTAINERS=(
  "bankflow-mysql"
  "bankflow-redis"
  "bankflow-kafka"
  "bankflow-kafka-ui"
  "bankflow-redis-ui"
  "bankflow-mailhog"
  "bankflow-prometheus"
  "bankflow-grafana"
  "bankflow-sonar-db"
  "bankflow-sonarqube"
)

docker compose -f "${COMPOSE_FILE}" up -d

echo "Waiting for services to be healthy..."

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

BankFlow infrastructure is ready.

MySQL:           localhost:3306
Redis:           localhost:6379
Kafka broker:    localhost:9092
Kafka UI:        http://localhost:8090
Redis Commander: http://localhost:8091
MailHog UI:      http://localhost:8025
Prometheus:      http://localhost:9090
Grafana:         http://localhost:3000   (admin / bankflow_grafana)
SonarQube:       http://localhost:9000   (admin / admin)
EOF
