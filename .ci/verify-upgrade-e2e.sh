#!/usr/bin/env bash
set -euo pipefail

SERIAL="${1:-emulator-5554}"
APP_ID="com.androbot.app"
TEST_RECEIVER="${APP_ID}/.TestControlReceiver"
TRUSTED_SENDER="15551234567"
OLD_VERSION_CODE="${OLD_VERSION_CODE:-5001}"
NEW_VERSION_CODE="${NEW_VERSION_CODE:-5002}"
OLD_VERSION_NAME="${OLD_VERSION_NAME:-e2e-old}"
NEW_VERSION_NAME="${NEW_VERSION_NAME:-e2e-new}"
TMP_DIR="$(mktemp -d)"
OLD_APK="${TMP_DIR}/app-debug-old.apk"
NEW_APK="${TMP_DIR}/app-debug-new.apk"

cleanup() {
  rm -rf "$TMP_DIR"
}
trap cleanup EXIT

trusted_state_line() {
  local label="$1"
  adb -s "$SERIAL" shell am broadcast -n "$TEST_RECEIVER" -a com.androbot.app.TEST_LOG_TRUSTED_STATE --es label "$label" >/dev/null
  adb -s "$SERIAL" logcat -d -s TestControlReceiver:I | awk -v needle="TRUSTED_STATE label=${label}" 'index($0, needle) { last = $0 } END { print last }'
}

trusted_count() {
  local line="$1"
  echo "$line" | sed -nE 's/.*count=([0-9]+).*/\1/p'
}

installed_version_code() {
  adb -s "$SERIAL" shell dumpsys package "$APP_ID" | sed -nE 's/.*versionCode=([0-9]+).*/\1/p' | head -n1 | tr -d '\r'
}

echo "Preparing clean install state..."
adb -s "$SERIAL" uninstall "$APP_ID" >/dev/null || true
adb -s "$SERIAL" logcat -c

echo "Building OLD APK (${OLD_VERSION_NAME}, code ${OLD_VERSION_CODE})..."
ANDROBOT_VERSION_CODE_OVERRIDE="$OLD_VERSION_CODE" \
  ANDROBOT_VERSION_NAME_OVERRIDE="$OLD_VERSION_NAME" \
  ./gradlew --no-daemon assembleDebug
cp app/build/outputs/apk/debug/app-debug.apk "$OLD_APK"

echo "Building NEW APK (${NEW_VERSION_NAME}, code ${NEW_VERSION_CODE})..."
ANDROBOT_VERSION_CODE_OVERRIDE="$NEW_VERSION_CODE" \
  ANDROBOT_VERSION_NAME_OVERRIDE="$NEW_VERSION_NAME" \
  ./gradlew --no-daemon assembleDebug
cp app/build/outputs/apk/debug/app-debug.apk "$NEW_APK"

echo "Installing OLD APK on ${SERIAL}..."
adb -s "$SERIAL" install "$OLD_APK" >/dev/null

old_installed_code="$(installed_version_code)"
echo "Installed OLD versionCode=${old_installed_code:-unknown}"
if [[ "$old_installed_code" != "$OLD_VERSION_CODE" ]]; then
  echo "E2E upgrade failed: expected old versionCode=$OLD_VERSION_CODE, got ${old_installed_code:-missing}."
  exit 1
fi

echo "Seeding trusted sender in OLD app state..."
adb -s "$SERIAL" shell am broadcast -n "$TEST_RECEIVER" -a com.androbot.app.TEST_ADD_TRUSTED --es sender "$TRUSTED_SENDER" >/dev/null
before_upgrade_line="$(trusted_state_line before_upgrade)"
before_upgrade_count="$(trusted_count "$before_upgrade_line")"
echo "Trusted sender count before upgrade: ${before_upgrade_count:-unknown}"
if [[ -z "$before_upgrade_count" || "$before_upgrade_count" -lt 1 ]]; then
  echo "E2E upgrade failed: expected at least one trusted sender before upgrade."
  adb -s "$SERIAL" logcat -d -s TestControlReceiver:I || true
  exit 1
fi

echo "Upgrading in place to NEW APK..."
adb -s "$SERIAL" install -r "$NEW_APK" >/dev/null

new_installed_code="$(installed_version_code)"
echo "Installed NEW versionCode=${new_installed_code:-unknown}"
if [[ "$new_installed_code" != "$NEW_VERSION_CODE" ]]; then
  echo "E2E upgrade failed: expected new versionCode=$NEW_VERSION_CODE, got ${new_installed_code:-missing}."
  exit 1
fi

after_upgrade_line="$(trusted_state_line after_upgrade)"
after_upgrade_count="$(trusted_count "$after_upgrade_line")"
echo "Trusted sender count after upgrade: ${after_upgrade_count:-unknown}"
if [[ -z "$after_upgrade_count" || "$after_upgrade_count" -lt 1 ]]; then
  echo "E2E upgrade failed: trusted sender state not preserved across upgrade."
  adb -s "$SERIAL" logcat -d -s TestControlReceiver:I || true
  exit 1
fi

echo "E2E upgrade verification passed."
