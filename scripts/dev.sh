#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

APK_PATH="${APK_PATH:-app/build/outputs/apk/debug/app-debug.apk}"
ADB_SERIAL="${DEVICE:-emulator:5555}"
PHONE_IP="${PHONE_IP:-}"
PHONE_PORT="${PHONE_PORT:-5555}"

require_docker() {
  if ! command -v docker >/dev/null 2>&1; then
    echo "Missing required command: docker"
    exit 1
  fi
}

compose() {
  "$ROOT_DIR/scripts/compose.sh" -f docker-compose.ci.yml "$@"
}

require_adb() {
  if ! command -v adb >/dev/null 2>&1; then
    echo "Missing required command: adb"
    echo "Install on macOS: brew install android-platform-tools"
    exit 1
  fi
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

ci_bash_no_deps() {
  require_docker
  compose run --rm --no-deps --entrypoint bash ci -lc "$1"
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

wait_for_emulator_local() {
  require_adb
  adb start-server >/dev/null
  for _ in $(seq 1 60); do
    if adb connect emulator:5555 >/dev/null 2>&1; then
      break
    fi
    sleep 2
  done
  for _ in $(seq 1 60); do
    if [ "$(adb -s "${ADB_SERIAL}" shell getprop sys.boot_completed | tr -d '\r')" = "1" ]; then
      return
    fi
    sleep 2
  done
  echo "Emulator did not finish booting in time."
  exit 1
}

build() {
  gradle_run assembleDebug
}

build_release() {
  gradle_run assembleRelease
}

build_bundle() {
  gradle_run bundleRelease
}

install_apk() {
  if [[ ! -f "$APK_PATH" ]]; then
    echo "APK not found at ${APK_PATH}. Building debug APK first..."
    build
  fi
  ensure_emulator_up
  wait_for_emulator_local
  adb -s "${ADB_SERIAL}" install -r "${APK_PATH}"
}

deploy_phone_tcp() {
  if [[ -z "$PHONE_IP" ]]; then
    echo "PHONE_IP is required for deploy."
    echo "Example: PHONE_IP=192.168.1.50 make deploy"
    exit 1
  fi

  if [[ ! -f "$APK_PATH" ]]; then
    echo "APK not found at ${APK_PATH}. Building debug APK first..."
    build
  fi

  require_adb
  adb start-server >/dev/null
  adb connect "${PHONE_IP}:${PHONE_PORT}"
  adb -s "${PHONE_IP}:${PHONE_PORT}" install -r "${APK_PATH}"
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
  echo "Checking local and Dockerized toolchain..."

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
  if command -v adb >/dev/null 2>&1; then
    echo "- adb (host): OK"
  else
    echo "- adb (host): MISSING (install: brew install android-platform-tools)"
  fi
  echo "- default emulator serial: ${ADB_SERIAL}"
}

usage() {
  cat <<USAGE
Usage: ./scripts/dev.sh <command>

Commands:
  doctor        Validate Dockerized toolchain
  build         Build debug APK (Gradle in ci container)
  build-release Build release APK (Gradle in ci container)
  build-bundle  Build release AAB (Gradle in ci container)
  install       Install debug APK to docker emulator via host adb
  deploy        Deploy debug APK to real phone via host adb TCP
  test-unit     Run JVM unit tests (Gradle in ci container)
  test-device   Run instrumentation tests (adb + Gradle in ci container)
  test          Run unit + instrumentation tests
  ci            Run Docker-based local CI (unit + instrumentation)

Optional env:
  APK_PATH=<path-to-apk>              Override APK path for install/deploy
  DEVICE=<adb-serial>                Override adb serial (default: emulator:5555)
  PHONE_IP=<phone-lan-ip>            Real phone IP for deploy command
  PHONE_PORT=<port>                  ADB TCP port for deploy (default: 5555)
  ANDROBOT_PLATFORM=<linux/*>        Override auto platform detection
USAGE
}

case "${1:-}" in
  doctor) doctor ;;
  build) build ;;
  build-release) build_release ;;
  build-bundle) build_bundle ;;
  install) install_apk ;;
  deploy) deploy_phone_tcp ;;
  test-unit) test_unit ;;
  test-device) test_device ;;
  test) test_all ;;
  ci) ci_docker ;;
  *) usage; exit 1 ;;
esac
