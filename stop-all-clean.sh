#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "${SCRIPT_DIR}"

read -r -p "This will stop BankFlow and DELETE local Docker data (containers, volumes, and orphaned resources). Type YES to continue: " CONFIRM
if [[ "${CONFIRM}" != "YES" ]]; then
  echo "Operation cancelled."
  exit 0
fi

docker compose \
  -f docker-compose.infrastructure.yml \
  -f docker-compose.services.yml \
  down -v --remove-orphans

echo "BankFlow services, infrastructure, and Docker data removed."
