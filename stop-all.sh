#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "${SCRIPT_DIR}"

docker compose \
  -f docker-compose.infrastructure.yml \
  -f docker-compose.services.yml \
  down

echo "BankFlow services and infrastructure stopped."
