#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

APK_PATH="app/build/outputs/apk/debug/app-debug.apk"
ADB_SERIAL="${DEVICE:-emulator:5555}"

require_docker() {
  if ! command -v docker >/dev/null 2>&1; then
    echo "Missing required command: docker"
    exit 1
  fi
}

compose() {
  "$ROOT_DIR/scripts/compose.sh" -f docker-compose.ci.yml "$@"
}

gradle_run() {
  "$ROOT_DIR/scripts/gradlew.sh" "$@"
}

ensure_emulator_up() {
  require_docker
  compose up -d emulator >/dev/null
}

ci_bash() {
  require_docker
  compose run --rm --entrypoint bash ci -lc "$1"
}

wait_for_emulator() {
  ci_bash "
    set -euo pipefail
    adb start-server >/dev/null
    for _ in \\$(seq 1 60); do
      if adb connect emulator:5555 >/dev/null 2>&1; then
        break
      fi
      sleep 2
    done
    for _ in \\$(seq 1 60); do
      if [ \\\"\\$(adb -s ${ADB_SERIAL} shell getprop sys.boot_completed | tr -d '\\\\r')\\\" = \\\"1\\\" ]; then
        exit 0
      fi
      sleep 2
    done
    echo 'Emulator did not finish booting in time.'
    exit 1
  "
}

build() {
  gradle_run assembleDebug
}

install_apk() {
  if [[ ! -f "$APK_PATH" ]]; then
    echo "APK not found. Building debug APK first..."
    build
  fi
  ensure_emulator_up
  wait_for_emulator
  ci_bash "
    set -euo pipefail
    adb start-server >/dev/null
    adb connect emulator:5555 >/dev/null || true
    adb -s ${ADB_SERIAL} install -r ${APK_PATH}
  "
}

test_unit() {
  gradle_run testDebugUnitTest
}

test_device() {
  ensure_emulator_up
  wait_for_emulator
  ci_bash "
    set -euo pipefail
    adb start-server >/dev/null
    adb connect emulator:5555 >/dev/null || true
    /workspace/scripts/ci-gradle.sh --no-daemon connectedDebugAndroidTest
  "
}

test_all() {
  test_unit
  test_device
}

ci_docker() {
  require_docker
  compose up --abort-on-container-exit --exit-code-from ci
}

doctor() {
  echo "Checking Dockerized toolchain..."

  if command -v docker >/dev/null 2>&1; then
    echo "- docker: OK"
  else
    echo "- docker: MISSING"
  fi

  if docker compose version >/dev/null 2>&1; then
    echo "- compose backend: local docker compose plugin"
  else
    echo "- compose backend: docker/compose container fallback"
  fi

  echo "- resolved platform: $($ROOT_DIR/scripts/compose.sh --print-platform)"
  echo "- override platform: ANDROBOT_PLATFORM=<linux/arm64|linux/amd64>"
  echo "- gradle: provided by /workspace/scripts/ci-gradle.sh"
  echo "- adb: provided by ci container"
  echo "- default emulator serial: ${ADB_SERIAL}"
}

usage() {
  cat <<USAGE
Usage: ./scripts/dev.sh <command>

Commands:
  doctor        Validate Dockerized toolchain
  build         Build debug APK (Gradle in ci container)
  install       Install debug APK to docker emulator via adb in ci container
  test-unit     Run JVM unit tests (Gradle in ci container)
  test-device   Run instrumentation tests (adb + Gradle in ci container)
  test          Run unit + instrumentation tests
  ci            Run Docker-based local CI (unit + instrumentation)

Optional env:
  DEVICE=<adb-serial>                Override adb serial (default: emulator:5555)
  ANDROBOT_PLATFORM=<linux/*>        Override auto platform detection
USAGE
}

case "${1:-}" in
  doctor) doctor ;;
  build) build ;;
  install) install_apk ;;
  test-unit) test_unit ;;
  test-device) test_device ;;
  test) test_all ;;
  ci) ci_docker ;;
  *) usage; exit 1 ;;
esac
