#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

if ! command -v docker >/dev/null 2>&1; then
  echo "Docker is required to run gradle from containers."
  exit 1
fi

resolve_gradle_platform() {
  if [[ -n "${ANDROBOT_PLATFORM:-}" ]]; then
    echo "$ANDROBOT_PLATFORM"
    return
  fi

  if [[ -n "${ANDROBOT_GRADLE_PLATFORM:-}" ]]; then
    echo "$ANDROBOT_GRADLE_PLATFORM"
    return
  fi

  case "$(uname -m)" in
    arm64|aarch64)
      # AGP/aapt2 in our containerized toolchain is currently most reliable on amd64.
      echo "linux/amd64"
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

GRADLE_PLATFORM="$(resolve_gradle_platform)"
GRADLE_JVMARGS="${ANDROBOT_GRADLE_JVMARGS:--Xmx1536m -Dfile.encoding=UTF-8}"

env ANDROBOT_PLATFORM="$GRADLE_PLATFORM" \
  "$ROOT_DIR/scripts/compose.sh" -f docker-compose.ci.yml run --rm --no-deps --entrypoint /workspace/scripts/ci-gradle.sh ci -Dorg.gradle.jvmargs="$GRADLE_JVMARGS" --no-daemon "$@"
