#!/usr/bin/env bash
set -euo pipefail

SERIAL="${1:-emulator-5554}"
APP_ID="${APP_ID:-com.androbot.app}"
APK_PATH="${APK_PATH:-app/build/outputs/apk/release/app-release.apk}"

require_apk() {
  if [[ ! -f "$APK_PATH" ]]; then
    echo "Release smoke test failed: APK not found at $APK_PATH"
    exit 1
  fi
}

wait_for_package() {
  local attempts="${1:-20}"
  local sleep_secs="${2:-1}"
  for _ in $(seq 1 "$attempts"); do
    if adb -s "$SERIAL" shell pm path "$APP_ID" >/dev/null 2>&1; then
      return 0
    fi
    sleep "$sleep_secs"
  done
  return 1
}

require_apk

echo "Release smoke: uninstalling existing package (if present)..."
adb -s "$SERIAL" uninstall "$APP_ID" >/dev/null 2>&1 || true

echo "Release smoke: fresh install from $APK_PATH ..."
adb -s "$SERIAL" install "$APK_PATH" >/dev/null
if ! wait_for_package; then
  echo "Release smoke test failed: package not visible after first install."
  exit 1
fi

echo "Release smoke: launching main activity..."
adb -s "$SERIAL" shell monkey -p "$APP_ID" -c android.intent.category.LAUNCHER 1 >/dev/null

echo "Release smoke: uninstalling package..."
adb -s "$SERIAL" uninstall "$APP_ID" >/dev/null
if adb -s "$SERIAL" shell pm path "$APP_ID" >/dev/null 2>&1; then
  echo "Release smoke test failed: package still present after uninstall."
  exit 1
fi

echo "Release smoke: reinstalling package..."
adb -s "$SERIAL" install "$APK_PATH" >/dev/null
if ! wait_for_package; then
  echo "Release smoke test failed: package not visible after reinstall."
  exit 1
fi

echo "Release smoke test passed."
