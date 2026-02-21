#!/usr/bin/env bash
set -euo pipefail

adb start-server

echo "Waiting for emulator container..."
for _ in $(seq 1 60); do
  if adb connect emulator:5555 >/dev/null 2>&1; then
    break
  fi
  sleep 5
done

echo "Connected devices:"
adb devices

echo "Waiting for boot completion..."
for _ in $(seq 1 60); do
  if [ "$(adb -s emulator:5555 shell getprop sys.boot_completed | tr -d '\r')" = "1" ]; then
    break
  fi
  sleep 5
done

/workspace/scripts/ci-gradle.sh --no-daemon testDebugUnitTest connectedDebugAndroidTest
