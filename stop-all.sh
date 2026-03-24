#!/usr/bin/env bash

set -euo pipefail

docker compose -f docker-compose.services.yml down
docker compose -f docker-compose.infrastructure.yml down

echo "BankFlow services and infrastructure stopped."
