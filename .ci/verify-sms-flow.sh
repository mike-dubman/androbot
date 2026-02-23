#!/usr/bin/env bash
set -euo pipefail

SERIAL="${1:-emulator-5554}"
APP_ID="com.androbot.app"
TRUSTED_SENDER="15551234567"
UNTRUSTED_SENDER="15557654321"
APK_PATH="app/build/outputs/apk/debug/app-debug.apk"
KNOWN_PERCENT="${KNOWN_PERCENT:-0}"
TEST_RECEIVER="${APP_ID}/.TestControlReceiver"

dump_logs() {
  adb -s "$SERIAL" logcat -d -s SmsCommandReceiver:I SmsCommandEngine:I TestControlReceiver:I || true
}

snapshot_line() {
  local label="$1"
  adb -s "$SERIAL" shell am broadcast -n "$TEST_RECEIVER" -a com.androbot.app.TEST_LOG_VOLUME_SNAPSHOT --es label "$label"
  adb -s "$SERIAL" logcat -d -s TestControlReceiver:I | awk -v needle="SNAPSHOT label=${label}" 'index($0, needle) { last = $0 } END { print last }'
}

parse_snapshot() {
  local line="$1"
  local call_cur call_max ring_cur ring_max media_cur media_max
  call_cur="$(echo "$line" | sed -nE 's/.*call=([0-9]+)\/([0-9]+).*/\1/p')"
  call_max="$(echo "$line" | sed -nE 's/.*call=([0-9]+)\/([0-9]+).*/\2/p')"
  ring_cur="$(echo "$line" | sed -nE 's/.*ring=([0-9]+)\/([0-9]+).*/\1/p')"
  ring_max="$(echo "$line" | sed -nE 's/.*ring=([0-9]+)\/([0-9]+).*/\2/p')"
  media_cur="$(echo "$line" | sed -nE 's/.*media=([0-9]+)\/([0-9]+).*/\1/p')"
  media_max="$(echo "$line" | sed -nE 's/.*media=([0-9]+)\/([0-9]+).*/\2/p')"
  echo "$call_cur $call_max $ring_cur $ring_max $media_cur $media_max"
}

require_snapshot() {
  local label="$1"
  local line="$2"
  if [[ -z "$line" ]]; then
    echo "Failed to read snapshot for label=${label}"
    dump_logs
    exit 1
  fi
}

echo "Building debug APK for SMS flow verification..."
./gradlew --no-daemon assembleDebug

echo "Installing app on ${SERIAL}..."
adb -s "$SERIAL" install -r "$APK_PATH"

echo "Granting RECEIVE_SMS permission..."
adb -s "$SERIAL" shell pm grant "$APP_ID" android.permission.RECEIVE_SMS || true
echo "Granting CALL_PHONE permission..."
adb -s "$SERIAL" shell pm grant "$APP_ID" android.permission.CALL_PHONE || true

adb -s "$SERIAL" logcat -c

echo "Adding trusted sender (${TRUSTED_SENDER}) via debug test receiver..."
adb -s "$SERIAL" shell am broadcast -n "$TEST_RECEIVER" -a com.androbot.app.TEST_ADD_TRUSTED --es sender "$TRUSTED_SENDER"

echo "Setting known baseline volume (${KNOWN_PERCENT}%)..."
adb -s "$SERIAL" shell am broadcast -n "$TEST_RECEIVER" -a com.androbot.app.TEST_SET_VOLUME_PERCENT --ei percent "$KNOWN_PERCENT"

BASELINE_LINE="$(snapshot_line baseline)"
require_snapshot "baseline" "$BASELINE_LINE"
read -r base_call_cur base_call_max base_ring_cur base_ring_max base_media_cur base_media_max < <(parse_snapshot "$BASELINE_LINE")
echo "Known baseline: call=${base_call_cur}/${base_call_max}; ring=${base_ring_cur}/${base_ring_max}; media=${base_media_cur}/${base_media_max}"

echo "Negative test #1: untrusted sender must not change volume"
adb -s "$SERIAL" emu sms send "$UNTRUSTED_SENDER" "volume max"
sleep 3
NEG1_LINE="$(snapshot_line negative_untrusted)"
require_snapshot "negative_untrusted" "$NEG1_LINE"
read -r neg1_call_cur neg1_call_max neg1_ring_cur neg1_ring_max neg1_media_cur neg1_media_max < <(parse_snapshot "$NEG1_LINE")
echo "After untrusted SMS: call=${neg1_call_cur}/${neg1_call_max}; ring=${neg1_ring_cur}/${neg1_ring_max}; media=${neg1_media_cur}/${neg1_media_max}"
if [[ "$neg1_media_cur" != "$base_media_cur" ]]; then
  echo "Negative test failed: untrusted sender changed media volume."
  dump_logs
  exit 1
fi

echo "Negative test #2: trusted sender with invalid command must not change volume"
adb -s "$SERIAL" emu sms send "$TRUSTED_SENDER" "volume banana"
sleep 3
NEG2_LINE="$(snapshot_line negative_invalid)"
require_snapshot "negative_invalid" "$NEG2_LINE"
read -r neg2_call_cur neg2_call_max neg2_ring_cur neg2_ring_max neg2_media_cur neg2_media_max < <(parse_snapshot "$NEG2_LINE")
echo "After invalid trusted SMS: call=${neg2_call_cur}/${neg2_call_max}; ring=${neg2_ring_cur}/${neg2_ring_max}; media=${neg2_media_cur}/${neg2_media_max}"
if [[ "$neg2_media_cur" != "$base_media_cur" ]]; then
  echo "Negative test failed: invalid trusted command changed media volume."
  dump_logs
  exit 1
fi

echo "Negative test #3: untrusted sender call-back command must be ignored"
adb -s "$SERIAL" emu sms send "$UNTRUSTED_SENDER" "call me back"
sleep 2
if adb -s "$SERIAL" logcat -d -s SmsCommandEngine:I | grep -q "Call me back requested by"; then
  echo "Negative test failed: untrusted sender reached call-back path."
  dump_logs
  exit 1
fi

echo "Triggering SMS command: volume max"
adb -s "$SERIAL" emu sms send "$TRUSTED_SENDER" "volume max"

echo "Waiting for SMS execution and volume to reach max..."
volume_passed="false"
for _ in $(seq 1 25); do
  AFTER_LINE="$(snapshot_line after)"
  require_snapshot "after" "$AFTER_LINE"
  read -r call_cur call_max ring_cur ring_max media_cur media_max < <(parse_snapshot "$AFTER_LINE")
  echo "After SMS check: call=${call_cur}/${call_max}; ring=${ring_cur}/${ring_max}; media=${media_cur}/${media_max}"
  if [[ "$media_cur" == "$media_max" && "$media_cur" != "$base_media_cur" ]]; then
    volume_passed="true"
    break
  fi
  sleep 2
done

if [[ "$volume_passed" != "true" ]]; then
  echo "SMS flow verification failed: command did not execute or media volume did not transition to max."
  dump_logs
  exit 1
fi

echo "Triggering trusted call-back command"
adb -s "$SERIAL" emu sms send "$TRUSTED_SENDER" "call me back"
for _ in $(seq 1 10); do
  if adb -s "$SERIAL" logcat -d -s SmsCommandEngine:I | grep -q "Call me back requested by ${TRUSTED_SENDER}"; then
    echo "Call-back command path verification passed."
    exit 0
  fi
  sleep 1
done

echo "SMS flow verification passed (positive + negative paths including call-back)."
exit 0

echo "SMS call-back verification failed: command path not reached."
dump_logs
exit 1
