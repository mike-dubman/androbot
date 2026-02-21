#!/usr/bin/env bash
set -euo pipefail

SERIAL="${1:-emulator-5554}"
ATTEMPTS="${2:-120}"
SLEEP_SECS="${3:-2}"

adb kill-server || true
adb start-server
adb wait-for-device

echo "Waiting for emulator to be fully online..."
for _ in $(seq 1 "$ATTEMPTS"); do
  state="$(adb -s "$SERIAL" get-state 2>/dev/null || true)"
  boot="$(adb -s "$SERIAL" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)"
  if [[ "$state" == "device" && "$boot" == "1" ]]; then
    break
  fi
  sleep "$SLEEP_SECS"
done

state="$(adb -s "$SERIAL" get-state 2>/dev/null || true)"
boot="$(adb -s "$SERIAL" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)"
if [[ "$state" != "device" || "$boot" != "1" ]]; then
  echo "Emulator did not become healthy in time."
  adb devices -l || true
  adb -s "$SERIAL" shell getprop || true
  exit 1
fi

echo "Checking package manager responsiveness..."
adb -s "$SERIAL" shell pm path android >/dev/null
