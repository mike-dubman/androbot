# Release

## Manual release workflow (recommended)

Trigger the GitHub Actions release pipeline manually:

```bash
make release-cli  # uses app/build.gradle.kts versionName, defaults to published
```

Optional inputs:

```bash
VERSION=0.2.0 RELEASE_PUBLISH=draft make release-cli
VERSION=0.2.0 RELEASE_PUBLISH=draft RELEASE_NOTES="Hotfix build" make release-cli
```

Note: APK files are published under GitHub Releases assets. They do not appear under the GitHub "Packages" tab unless you publish a package format (for example a container image or Maven artifact).

Workflow behavior:

- builds debug and release APK
- builds release AAB
- creates/updates tag `v<version>`
- creates GitHub release (published by default, draft optional)
- attaches APK/AAB assets
- writes release body with changelog and direct download links
- requires signing secrets and publishes a signed release APK
- publishes OTA metadata (`androbot-update.json`) and APK checksum

## Build Play artifact locally

Build release AAB:

```bash
make build-bundle
```

Output:

```text
app/build/outputs/bundle/release/app-release.aab
```

Optional signing env for local build:

```bash
export ANDROID_KEYSTORE_PATH=/absolute/path/upload-key.jks
export ANDROID_KEYSTORE_PASSWORD='***'
export ANDROID_KEY_ALIAS='upload'
export ANDROID_KEY_PASSWORD='***'
make build-bundle
```

If signing env vars are not set, the AAB is still built but not signed for Play upload.

## GitHub release workflow signing setup (required)

Configure a protected GitHub Actions environment named `production`, then add these
environment secrets:

- `ANDROID_KEYSTORE_BASE64`: base64 of your `.jks` upload keystore
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

Generate keystore + all secret values with Docker (recommended):

```bash
KEYSTORE_PASSWORD='***' KEY_PASSWORD='***' ./scripts/setup-signing-secrets.sh > .secrets/github-production-secrets.txt
```

Apply generated values to GitHub environment secrets (`production`):

```bash
eval "$(sed -nE 's/^[[:space:]]*(ANDROID_[A-Z_]+)=(.*)$/\1='\''\2'\''/p' .secrets/github-production-secrets.txt)"

gh secret set ANDROID_KEYSTORE_BASE64 --env production --body "$ANDROID_KEYSTORE_BASE64"
gh secret set ANDROID_KEYSTORE_PASSWORD --env production --body "$ANDROID_KEYSTORE_PASSWORD"
gh secret set ANDROID_KEY_ALIAS --env production --body "$ANDROID_KEY_ALIAS"
gh secret set ANDROID_KEY_PASSWORD --env production --body "$ANDROID_KEY_PASSWORD"
```

The release workflow now fails fast if any of these secrets are missing.
Published release assets include:

- `androbot-debug-v<version>.apk`
- `androbot-release-v<version>.apk` (signed)
- `androbot-release-latest.apk` (signed latest alias)
- `androbot-release-v<version>.apk.sha256`
- `androbot-update.json` (OTA metadata for in-app updater)
- `androbot-release-v<version>.aab`

OTA metadata URL used by app updater:

- `https://github.com/mike-dubman/androbot/releases/latest/download/androbot-update.json`

## Google Play Console checklist

- Create Play Console app entry and complete app profile metadata.
- Ensure package id stays stable: `com.androbot.app`.
- Ensure `versionCode` is strictly increasing for every upload.
- Upload signed `app-release.aab` to Internal testing first.
- Complete Data safety form.
- Provide Privacy Policy URL.
- Complete content rating questionnaire.
- Declare permissions usage clearly (SMS-related permissions are sensitive and reviewed).
- Add store assets: icon, screenshots, short/full description.
- Run internal test rollout and verify install/upgrade behavior.
- Promote to production when policy checks pass.

## Legacy local release script

Create and publish a GitHub Release (tag + generated notes + debug APK asset):

```bash
VERSION=0.2.0 make release
```

Requirements:

- Clean git working tree
- `gh auth login` completed
- Push access to `origin`
