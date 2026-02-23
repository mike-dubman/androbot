#!/usr/bin/env bash
set -euo pipefail

SERIAL="${1:-emulator-5554}"
ATTEMPTS="${2:-120}"
SLEEP_SECS="${3:-2}"

echo "Waiting for emulator to be fully online..."
for _ in $(seq 1 "$ATTEMPTS"); do
  state="$(adb -s "$SERIAL" get-state 2>/dev/null || true)"
  if [[ "$state" == "offline" ]]; then
    adb reconnect offline >/dev/null 2>&1 || true
    sleep "$SLEEP_SECS"
    continue
  fi

  boot=""
  if [[ "$state" == "device" ]]; then
    boot="$(adb -s "$SERIAL" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)"
  fi
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
for _ in $(seq 1 "$ATTEMPTS"); do
  if adb -s "$SERIAL" shell pm path android >/dev/null 2>&1; then
    exit 0
  fi
  sleep "$SLEEP_SECS"
done

echo "Package manager did not become responsive in time."
adb -s "$SERIAL" shell getprop || true
exit 1
