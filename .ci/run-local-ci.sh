#!/usr/bin/env bash
set -euo pipefail

adb start-server

echo "Waiting for emulator container..."
for _ in $(seq 1 60); do
  adb connect emulator:5555 >/dev/null 2>&1 || true
  state="$(adb devices | awk '$1=="emulator:5555" {print $2}')"
  if [ "$state" = "device" ]; then
    break
  fi
  sleep 5
done

echo "Connected devices:"
adb devices

state="$(adb devices | awk '$1=="emulator:5555" {print $2}')"
if [ "$state" != "device" ]; then
  echo "Emulator did not reach adb device state. Current state: ${state:-missing}"
  exit 1
fi

echo "Waiting for boot completion..."
for _ in $(seq 1 60); do
  if [ "$(adb -s emulator:5555 shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; then
    break
  fi
  sleep 5
done

if [ "$(adb -s emulator:5555 shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" != "1" ]; then
  echo "Emulator never completed boot."
  exit 1
fi

/workspace/scripts/ci-gradle.sh --no-daemon testDebugUnitTest connectedDebugAndroidTest
