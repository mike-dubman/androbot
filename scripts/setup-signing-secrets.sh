#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
OUTPUT_DIR_DEFAULT="${ROOT_DIR}/.secrets"

KEYSTORE_DIR="${KEYSTORE_DIR:-$OUTPUT_DIR_DEFAULT}"
KEYSTORE_FILE="${KEYSTORE_FILE:-androbot-upload-key.jks}"
KEY_ALIAS="${KEY_ALIAS:-upload}"
KEYSTORE_PASSWORD="${KEYSTORE_PASSWORD:-}"
KEY_PASSWORD="${KEY_PASSWORD:-}"
KEY_DNAME="${KEY_DNAME:-CN=Androbot, OU=Mobile, O=Androbot, L=NA, ST=NA, C=US}"

usage() {
  cat <<USAGE
Usage:
  KEYSTORE_PASSWORD='<store-pass>' KEY_PASSWORD='<key-pass>' ./scripts/setup-signing-secrets.sh

Optional env:
  KEYSTORE_DIR=<dir>         Output directory (default: ./.secrets)
  KEYSTORE_FILE=<name>       Keystore file name (default: androbot-upload-key.jks)
  KEY_ALIAS=<alias>          Key alias (default: upload)
  KEY_DNAME=<distinguished>  keytool DName

Output:
  - Creates keystore via Docker (eclipse-temurin:17-jdk)
  - Prints values for GitHub environment secrets:
      ANDROID_KEYSTORE_BASE64
      ANDROID_KEYSTORE_PASSWORD
      ANDROID_KEY_ALIAS
      ANDROID_KEY_PASSWORD
USAGE
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

if ! command -v docker >/dev/null 2>&1; then
  echo "Missing required command: docker"
  echo "Install on macOS: brew install --cask docker"
  exit 1
fi

if [[ -z "$KEYSTORE_PASSWORD" || -z "$KEY_PASSWORD" ]]; then
  echo "KEYSTORE_PASSWORD and KEY_PASSWORD are required."
  usage
  exit 1
fi

mkdir -p "$KEYSTORE_DIR"
KEYSTORE_PATH="${KEYSTORE_DIR}/${KEYSTORE_FILE}"

if [[ -f "$KEYSTORE_PATH" ]]; then
  echo "Keystore already exists: $KEYSTORE_PATH"
  echo "Refusing to overwrite existing file."
  exit 1
fi

echo "Generating keystore: $KEYSTORE_PATH"
docker run --rm -v "${KEYSTORE_DIR}:/work" -w /work eclipse-temurin:17-jdk \
  keytool -genkeypair -v \
    -keystore "$KEYSTORE_FILE" \
    -storetype JKS \
    -storepass "$KEYSTORE_PASSWORD" \
    -alias "$KEY_ALIAS" \
    -keypass "$KEY_PASSWORD" \
    -keyalg RSA \
    -keysize 4096 \
    -validity 10000 \
    -dname "$KEY_DNAME" >/dev/null

if [[ ! -f "$KEYSTORE_PATH" ]]; then
  echo "Failed to create keystore at $KEYSTORE_PATH"
  exit 1
fi

ANDROID_KEYSTORE_BASE64="$(base64 < "$KEYSTORE_PATH" | tr -d '\r\n')"

cat <<EOF

Created keystore:
  $KEYSTORE_PATH

Set these GitHub environment secrets in 'production':
  ANDROID_KEYSTORE_BASE64=$ANDROID_KEYSTORE_BASE64
  ANDROID_KEYSTORE_PASSWORD=$KEYSTORE_PASSWORD
  ANDROID_KEY_ALIAS=$KEY_ALIAS
  ANDROID_KEY_PASSWORD=$KEY_PASSWORD

Suggested backup:
  - Store $KEYSTORE_PATH and both passwords in a password manager + offline backup.
EOF
