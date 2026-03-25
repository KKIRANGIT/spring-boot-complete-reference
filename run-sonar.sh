#!/usr/bin/env bash
set -euo pipefail

if [[ -z "${SONAR_TOKEN:-}" ]]; then
  echo "SONAR_TOKEN is not set. Export it first, then rerun this script."
  echo "Example: export SONAR_TOKEN=your-generated-sonarqube-token"
  exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "${SCRIPT_DIR}/bankflow-parent"

mvn clean verify sonar:sonar \
  -Dsonar.host.url=http://localhost:9000 \
  -Dsonar.login="${SONAR_TOKEN}"
