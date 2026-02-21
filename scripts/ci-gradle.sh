#!/usr/bin/env bash
set -euo pipefail

GRADLE_VERSION="8.10.2"
GRADLE_DIR="/tmp/gradle-${GRADLE_VERSION}"
GRADLE_BIN="${GRADLE_DIR}/bin/gradle"

if ! command -v gradle >/dev/null 2>&1; then
  if [[ ! -x "$GRADLE_BIN" ]]; then
    if ! command -v curl >/dev/null 2>&1 || ! command -v unzip >/dev/null 2>&1; then
      echo "Missing curl/unzip required to bootstrap Gradle in CI container."
      exit 1
    fi
    curl -fsSL "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip" -o "/tmp/gradle-${GRADLE_VERSION}.zip"
    unzip -q -o "/tmp/gradle-${GRADLE_VERSION}.zip" -d /tmp
  fi
  export PATH="${GRADLE_DIR}/bin:${PATH}"
fi

exec gradle "$@"
