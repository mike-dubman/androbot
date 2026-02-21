#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

VERSION_INPUT="${1:-${VERSION:-}}"
if [[ -z "$VERSION_INPUT" ]]; then
  echo "Usage: VERSION=0.2.0 make release"
  echo "   or: ./scripts/release.sh 0.2.0"
  exit 1
fi

if [[ "$VERSION_INPUT" =~ ^v ]]; then
  TAG="$VERSION_INPUT"
else
  TAG="v$VERSION_INPUT"
fi

if ! command -v git >/dev/null 2>&1; then
  echo "git is required"
  exit 1
fi
if ! command -v gh >/dev/null 2>&1; then
  echo "gh (GitHub CLI) is required"
  exit 1
fi

if [[ -n "$(git status --porcelain)" ]]; then
  echo "Working tree is dirty. Commit or stash changes first."
  exit 1
fi

if git rev-parse "$TAG" >/dev/null 2>&1; then
  echo "Tag $TAG already exists locally"
  exit 1
fi

if git ls-remote --exit-code --tags origin "refs/tags/$TAG" >/dev/null 2>&1; then
  echo "Tag $TAG already exists on origin"
  exit 1
fi

REMOTE_URL="$(git remote get-url origin)"
REPO_SLUG="${REMOTE_URL#https://github.com/}"
REPO_SLUG="${REPO_SLUG%.git}"
if [[ "$REPO_SLUG" == "$REMOTE_URL" ]]; then
  # fallback for SSH format
  REPO_SLUG="${REMOTE_URL#git@github.com:}"
  REPO_SLUG="${REPO_SLUG%.git}"
fi

APK_PATH="app/build/outputs/apk/debug/app-debug.apk"
if [[ ! -f "$APK_PATH" ]]; then
  echo "APK not found, building first..."
  ./scripts/dev.sh build
fi

ASSET_NAME="androbot-${TAG}-debug.apk"

git tag -a "$TAG" -m "Androbot $TAG"
git push origin "$TAG"

gh release create "$TAG" \
  "$APK_PATH#$ASSET_NAME" \
  --repo "$REPO_SLUG" \
  --title "Androbot $TAG" \
  --generate-notes

echo "Release published: $TAG"
