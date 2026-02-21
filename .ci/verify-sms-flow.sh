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

  local current speaker default inferred_max
  current="$(echo "$line" | sed -E 's/.*Current:[[:space:]]*([0-9]+).*/\1/')"
  speaker="$(echo "$line" | sed -nE 's/.*\(speaker\):[[:space:]]*([0-9]+).*/\1/p')"
  default="$(echo "$line" | sed -nE 's/.*\(default\):[[:space:]]*([0-9]+).*/\1/p')"

  if [[ -z "$speaker" ]]; then speaker="$current"; fi
  if [[ -z "$default" ]]; then default="$current"; fi

  inferred_max="$current"
  if (( speaker > inferred_max )); then inferred_max="$speaker"; fi
  if (( default > inferred_max )); then inferred_max="$default"; fi

  echo "$current $speaker $default $inferred_max"
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
read -r call_current call_speaker call_default call_max < <(extract_stream_values "STREAM_VOICE_CALL")
read -r ring_current ring_speaker ring_default ring_max < <(extract_stream_values "STREAM_RING")
echo "Before SMS: call cur=${call_current}, spk=${call_speaker}, def=${call_default}, max=${call_max}; ring cur=${ring_current}, spk=${ring_speaker}, def=${ring_default}, max=${ring_max}"
if [[ "$call_speaker" == "$call_max" && "$call_default" == "$call_max" && "$ring_speaker" == "$ring_max" && "$ring_default" == "$ring_max" ]]; then
  echo "Precondition failed: volumes are already max before SMS command."
  exit 1
fi

echo "Triggering SMS command: volume max"
adb -s "$SERIAL" emu sms send "$TRUSTED_SENDER" "volume max"

echo "Waiting for SMS flow to set volume to max..."
for _ in $(seq 1 20); do
  read -r call_current call_speaker call_default call_max < <(extract_stream_values "STREAM_VOICE_CALL")
  read -r ring_current ring_speaker ring_default ring_max < <(extract_stream_values "STREAM_RING")
  echo "After SMS check: call cur=${call_current}, spk=${call_speaker}, def=${call_default}, max=${call_max}; ring cur=${ring_current}, spk=${ring_speaker}, def=${ring_default}, max=${ring_max}"
  if [[ "$call_speaker" == "$call_max" && "$call_default" == "$call_max" && "$ring_speaker" == "$ring_max" && "$ring_default" == "$ring_max" ]]; then
    echo "SMS flow verification passed."
    exit 0
  fi
  sleep 2
done

echo "SMS flow verification failed: volume was not set to max in time."
exit 1
