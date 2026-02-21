#!/usr/bin/env bash
set -euo pipefail

SERIAL="${1:-emulator-5554}"
APP_ID="com.androbot.app"
TRUSTED_SENDER="15551234567"
APK_PATH="app/build/outputs/apk/debug/app-debug.apk"
KNOWN_PERCENT="${KNOWN_PERCENT:-0}"

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

echo "Setting known baseline volume (${KNOWN_PERCENT}%)..."
SET_OUT="$(adb -s "$SERIAL" shell am broadcast -a com.androbot.app.TEST_SET_VOLUME_PERCENT --ei percent "$KNOWN_PERCENT")"
echo "$SET_OUT"

read -r known_call_cur known_call_spk known_call_def known_call_max < <(extract_stream_values "STREAM_VOICE_CALL")
read -r known_ring_cur known_ring_spk known_ring_def known_ring_max < <(extract_stream_values "STREAM_RING")
echo "Known baseline: call cur=${known_call_cur}, spk=${known_call_spk}, def=${known_call_def}, max=${known_call_max}; ring cur=${known_ring_cur}, spk=${known_ring_spk}, def=${known_ring_def}, max=${known_ring_max}"

echo "Triggering SMS command: volume max"
adb -s "$SERIAL" emu sms send "$TRUSTED_SENDER" "volume max"

echo "Waiting for SMS flow to set volume to max and differ from known baseline..."
for _ in $(seq 1 25); do
  read -r call_cur call_spk call_def call_max < <(extract_stream_values "STREAM_VOICE_CALL")
  read -r ring_cur ring_spk ring_def ring_max < <(extract_stream_values "STREAM_RING")

  echo "After SMS check: call cur=${call_cur}, spk=${call_spk}, def=${call_def}, max=${call_max}; ring cur=${ring_cur}, spk=${ring_spk}, def=${ring_def}, max=${ring_max}"

  call_is_max=false
  ring_is_max=false
  call_changed=false
  ring_changed=false

  if [[ "$call_spk" == "$call_max" && "$call_def" == "$call_max" ]]; then
    call_is_max=true
  fi
  if [[ "$ring_spk" == "$ring_max" && "$ring_def" == "$ring_max" ]]; then
    ring_is_max=true
  fi

  if [[ "$call_cur" != "$known_call_cur" || "$call_spk" != "$known_call_spk" || "$call_def" != "$known_call_def" ]]; then
    call_changed=true
  fi
  if [[ "$ring_cur" != "$known_ring_cur" || "$ring_spk" != "$known_ring_spk" || "$ring_def" != "$known_ring_def" ]]; then
    ring_changed=true
  fi

  if [[ "$call_is_max" == true && "$ring_is_max" == true && ( "$call_changed" == true || "$ring_changed" == true ) ]]; then
    echo "SMS flow verification passed."
    exit 0
  fi

  sleep 2
done

echo "SMS flow verification failed: volume was not set to max (or remained at known baseline)."
exit 1
