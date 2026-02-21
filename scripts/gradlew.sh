#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

if ! command -v docker >/dev/null 2>&1; then
  echo "Docker is required to run gradle from containers."
  exit 1
fi

"$ROOT_DIR/scripts/compose.sh" -f docker-compose.ci.yml run --rm --no-deps --entrypoint /workspace/scripts/ci-gradle.sh ci --no-daemon "$@"
