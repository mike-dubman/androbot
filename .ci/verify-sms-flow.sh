#!/usr/bin/env bash
set -euo pipefail

SERIAL="${1:-emulator-5554}"
APP_ID="com.androbot.app"
TRUSTED_SENDER="+15551234567"
APK_PATH="app/build/outputs/apk/debug/app-debug.apk"

echo "Building debug APK for SMS flow verification..."
./gradlew --no-daemon assembleDebug

echo "Installing app on ${SERIAL}..."
adb -s "$SERIAL" install -r "$APK_PATH"

echo "Granting RECEIVE_SMS permission..."
adb -s "$SERIAL" shell pm grant "$APP_ID" android.permission.RECEIVE_SMS || true

echo "Adding trusted sender (${TRUSTED_SENDER}) via debug test receiver..."
ADD_OUT="$(adb -s "$SERIAL" shell am broadcast -a com.androbot.app.TEST_ADD_TRUSTED --es sender "$TRUSTED_SENDER")"
echo "$ADD_OUT"

echo "Setting initial volume to minimum..."
SET_OUT="$(adb -s "$SERIAL" shell am broadcast -a com.androbot.app.TEST_SET_VOLUME_PERCENT --ei percent 0)"
echo "$SET_OUT"

echo "Triggering SMS command: volume max"
adb -s "$SERIAL" emu sms send "$TRUSTED_SENDER" "volume max"

echo "Waiting for SMS flow to set volume to max..."
for _ in $(seq 1 20); do
  ASSERT_OUT="$(adb -s "$SERIAL" shell am broadcast -a com.androbot.app.TEST_ASSERT_VOLUME_MAX)"
  echo "$ASSERT_OUT"
  if echo "$ASSERT_OUT" | grep -q "result=1"; then
    echo "SMS flow verification passed."
    exit 0
  fi
  sleep 2
done

echo "SMS flow verification failed: volume was not set to max in time."
exit 1
