#!/usr/bin/env bash
set -euo pipefail

SERIAL="${1:-emulator-5554}"
APP_ID="com.androbot.app"
TEST_RECEIVER="${APP_ID}/.TestControlReceiver"
TRUSTED_SENDER="15551234567"
APK_PATH="app/build/outputs/apk/debug/app-debug.apk"

trusted_state_line() {
  local label="$1"
  adb -s "$SERIAL" shell am broadcast -n "$TEST_RECEIVER" -a com.androbot.app.TEST_LOG_TRUSTED_STATE --es label "$label" >/dev/null
  adb -s "$SERIAL" logcat -d -s TestControlReceiver:I | awk -v needle="TRUSTED_STATE label=${label}" 'index($0, needle) { last = $0 } END { print last }'
}

trusted_count() {
  local line="$1"
  echo "$line" | sed -nE 's/.*count=([0-9]+).*/\1/p'
}

echo "Building debug APK for lifecycle verification..."
./gradlew --no-daemon assembleDebug

echo "Installing app on ${SERIAL}..."
adb -s "$SERIAL" install -r "$APK_PATH"

adb -s "$SERIAL" logcat -c

echo "Adding trusted sender for persistence check..."
adb -s "$SERIAL" shell am broadcast -n "$TEST_RECEIVER" -a com.androbot.app.TEST_ADD_TRUSTED --es sender "$TRUSTED_SENDER" >/dev/null

before_kill_line="$(trusted_state_line before_kill)"
before_kill_count="$(trusted_count "$before_kill_line")"
echo "Trusted sender count before kill: ${before_kill_count:-unknown}"
if [[ -z "${before_kill_count}" || "${before_kill_count}" -lt 1 ]]; then
  echo "Lifecycle verification failed: expected at least one trusted sender before kill."
  adb -s "$SERIAL" logcat -d -s TestControlReceiver:I || true
  exit 1
fi

echo "Killing app process..."
adb -s "$SERIAL" shell am kill "$APP_ID"
sleep 1

after_kill_line="$(trusted_state_line after_kill)"
after_kill_count="$(trusted_count "$after_kill_line")"
echo "Trusted sender count after kill: ${after_kill_count:-unknown}"
if [[ -z "${after_kill_count}" || "${after_kill_count}" -lt 1 ]]; then
  echo "Lifecycle verification failed: trusted senders were not preserved after kill."
  adb -s "$SERIAL" logcat -d -s TestControlReceiver:I || true
  exit 1
fi

echo "Upgrading app in place (adb install -r)..."
adb -s "$SERIAL" install -r "$APK_PATH" >/dev/null

after_upgrade_line="$(trusted_state_line after_upgrade)"
after_upgrade_count="$(trusted_count "$after_upgrade_line")"
echo "Trusted sender count after upgrade: ${after_upgrade_count:-unknown}"
if [[ -z "${after_upgrade_count}" || "${after_upgrade_count}" -lt 1 ]]; then
  echo "Lifecycle verification failed: trusted senders were not preserved after upgrade."
  adb -s "$SERIAL" logcat -d -s TestControlReceiver:I || true
  exit 1
fi

echo "Uninstalling app..."
adb -s "$SERIAL" uninstall "$APP_ID" >/dev/null || true

echo "Reinstalling app..."
adb -s "$SERIAL" install "$APK_PATH" >/dev/null

after_reinstall_line="$(trusted_state_line after_reinstall)"
after_reinstall_count="$(trusted_count "$after_reinstall_line")"
echo "Trusted sender count after reinstall: ${after_reinstall_count:-unknown}"
if [[ -z "${after_reinstall_count}" ]]; then
  echo "Lifecycle verification failed: could not read trusted sender state after reinstall."
  adb -s "$SERIAL" logcat -d -s TestControlReceiver:I || true
  exit 1
fi
if [[ "${after_reinstall_count}" -ne 0 ]]; then
  echo "Lifecycle verification failed: expected trusted sender count to reset after uninstall/reinstall."
  adb -s "$SERIAL" logcat -d -s TestControlReceiver:I || true
  exit 1
fi

echo "App lifecycle verification passed."
