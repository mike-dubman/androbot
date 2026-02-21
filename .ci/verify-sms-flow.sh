#!/usr/bin/env bash
set -euo pipefail

SERIAL="${1:-emulator-5554}"
APP_ID="com.androbot.app"
TRUSTED_SENDER="15551234567"
APK_PATH="app/build/outputs/apk/debug/app-debug.apk"
KNOWN_PERCENT="${KNOWN_PERCENT:-0}"

snapshot_line() {
  local label="$1"
  adb -s "$SERIAL" shell am broadcast -a com.androbot.app.TEST_LOG_VOLUME_SNAPSHOT --es label "$label" >/dev/null
  adb -s "$SERIAL" logcat -d -s TestControlReceiver:I | grep "SNAPSHOT label=$label" | tail -n 1
}

parse_snapshot() {
  local line="$1"
  local call_cur call_max ring_cur ring_max
  call_cur="$(echo "$line" | sed -nE 's/.*call=([0-9]+)\/([0-9]+).*/\1/p')"
  call_max="$(echo "$line" | sed -nE 's/.*call=([0-9]+)\/([0-9]+).*/\2/p')"
  ring_cur="$(echo "$line" | sed -nE 's/.*ring=([0-9]+)\/([0-9]+).*/\1/p')"
  ring_max="$(echo "$line" | sed -nE 's/.*ring=([0-9]+)\/([0-9]+).*/\2/p')"
  echo "$call_cur $call_max $ring_cur $ring_max"
}

echo "Building debug APK for SMS flow verification..."
./gradlew --no-daemon assembleDebug

echo "Installing app on ${SERIAL}..."
adb -s "$SERIAL" install -r "$APK_PATH"

echo "Granting RECEIVE_SMS permission..."
adb -s "$SERIAL" shell pm grant "$APP_ID" android.permission.RECEIVE_SMS || true

adb -s "$SERIAL" logcat -c

echo "Adding trusted sender (${TRUSTED_SENDER}) via debug test receiver..."
adb -s "$SERIAL" shell am broadcast -a com.androbot.app.TEST_ADD_TRUSTED --es sender "$TRUSTED_SENDER" >/dev/null

echo "Setting known baseline volume (${KNOWN_PERCENT}%)..."
adb -s "$SERIAL" shell am broadcast -a com.androbot.app.TEST_SET_VOLUME_PERCENT --ei percent "$KNOWN_PERCENT" >/dev/null

BASELINE_LINE="$(snapshot_line baseline)"
read -r base_call_cur base_call_max base_ring_cur base_ring_max < <(parse_snapshot "$BASELINE_LINE")
echo "Known baseline: call=${base_call_cur}/${base_call_max}; ring=${base_ring_cur}/${base_ring_max}"

echo "Triggering SMS command: volume max"
adb -s "$SERIAL" emu sms send "$TRUSTED_SENDER" "volume max"

echo "Waiting for SMS execution and volume to reach max..."
for _ in $(seq 1 25); do
  if adb -s "$SERIAL" logcat -d -s SmsCommandReceiver:I | grep -q "Command from .* -> EXECUTED"; then
    AFTER_LINE="$(snapshot_line after)"
    read -r call_cur call_max ring_cur ring_max < <(parse_snapshot "$AFTER_LINE")
    echo "After SMS check: call=${call_cur}/${call_max}; ring=${ring_cur}/${ring_max}"

    if [[ "$call_cur" == "$call_max" && "$ring_cur" == "$ring_max" && ( "$call_cur" != "$base_call_cur" || "$ring_cur" != "$base_ring_cur" ) ]]; then
      echo "SMS flow verification passed."
      exit 0
    fi
  fi
  sleep 2
done

echo "SMS flow verification failed: command did not execute or volume did not transition to max."
adb -s "$SERIAL" logcat -d -s SmsCommandReceiver:I TestControlReceiver:I || true
exit 1
