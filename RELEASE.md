# Release

## Manual release workflow (recommended)

Trigger the GitHub Actions release pipeline manually:

```bash
VERSION=0.2.0 make release-cli  # defaults to published
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

## GitHub release workflow signing setup (optional)

To make release workflow produce signed artifacts, add repository secrets:

- `ANDROID_KEYSTORE_BASE64`: base64 of your `.jks` upload keystore
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

Generate base64:

```bash
base64 -i upload-key.jks | tr -d '\n'
```

Without these secrets, workflow still publishes unsigned APK/AAB assets.

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
