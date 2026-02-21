#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

resolve_platform() {
  if [[ -n "${ANDROBOT_PLATFORM:-}" ]]; then
    echo "$ANDROBOT_PLATFORM"
    return
  fi

  case "$(uname -m)" in
    arm64|aarch64)
      echo "linux/arm64"
      ;;
    x86_64|amd64)
      echo "linux/amd64"
      ;;
    *)
      echo "Unsupported host architecture: $(uname -m)" >&2
      exit 1
      ;;
  esac
}

PLATFORM="$(resolve_platform)"

if [[ "${1:-}" == "--print-platform" ]]; then
  echo "$PLATFORM"
  exit 0
fi

if ! command -v docker >/dev/null 2>&1; then
  echo "Docker is required."
  exit 1
fi

if docker compose version >/dev/null 2>&1; then
  exec env ANDROBOT_PLATFORM="$PLATFORM" docker compose "$@"
fi

# Fallback: run Compose from container, so local compose plugin is not required.
exec docker run --rm \
  -e ANDROBOT_PLATFORM="$PLATFORM" \
  -v /var/run/docker.sock:/var/run/docker.sock \
  -v "$ROOT_DIR:$ROOT_DIR" \
  -w "$ROOT_DIR" \
  docker/compose:2.40.3 "$@"
