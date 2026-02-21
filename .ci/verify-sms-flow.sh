#!/usr/bin/env bash
set -euo pipefail

SERIAL="${1:-emulator-5554}"
APP_ID="com.androbot.app"
TRUSTED_SENDER="15551234567"
APK_PATH="app/build/outputs/apk/debug/app-debug.apk"

extract_stream_values() {
  local stream_name="$1"
  local line
  line="$(adb -s "$SERIAL" shell dumpsys audio \
    | awk -v stream="$stream_name" '
        $0 ~ "- "stream":" {flag=1; next}
        flag && /Current:/ {print; exit}
      ')"

  local current max
  current="$(echo "$line" | sed -E 's/.*Current:[[:space:]]*([0-9]+).*/\1/')"
  max="$(echo "$line" | sed -E 's/.*Max:[[:space:]]*([0-9]+).*/\1/')"
  echo "$current $max"
}

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

echo "Ensuring precondition: volumes are not max..."
read -r call_current call_max < <(extract_stream_values "STREAM_VOICE_CALL")
read -r ring_current ring_max < <(extract_stream_values "STREAM_RING")
echo "Before SMS: call=${call_current}/${call_max}, ring=${ring_current}/${ring_max}"
if [[ "$call_current" == "$call_max" && "$ring_current" == "$ring_max" ]]; then
  echo "Precondition failed: volumes are already max before SMS command."
  exit 1
fi

echo "Triggering SMS command: volume max"
adb -s "$SERIAL" emu sms send "$TRUSTED_SENDER" "volume max"

echo "Waiting for SMS flow to set volume to max..."
for _ in $(seq 1 20); do
  read -r call_current call_max < <(extract_stream_values "STREAM_VOICE_CALL")
  read -r ring_current ring_max < <(extract_stream_values "STREAM_RING")
  echo "After SMS check: call=${call_current}/${call_max}, ring=${ring_current}/${ring_max}"
  if [[ "$call_current" == "$call_max" && "$ring_current" == "$ring_max" ]]; then
    echo "SMS flow verification passed."
    exit 0
  fi
  sleep 2
done

echo "SMS flow verification failed: volume was not set to max in time."
exit 1
