# Androbot
[![Android CI](https://github.com/mike-dubman/androbot/actions/workflows/android.yml/badge.svg)](https://github.com/mike-dubman/androbot/actions/workflows/android.yml)

Androbot is an Android automation app that listens for incoming SMS and executes a small, explicit set of device actions when messages come from trusted senders.

## Goals

- Enable remote device control via SMS for predefined commands.
- Keep execution logic strict, predictable, and easy to audit.
- Make local and CI testing reproducible, including emulator-based tests in Docker.

## Scope

Current scope is intentionally narrow:

- Trigger source: incoming SMS (`SMS_RECEIVED`).
- Authorization: trusted sender allowlist (runtime-managed in app storage).
- Supported action commands:
  - `volume max`
  - `volume min`
  - `volume <0-100>`
- Trusted sender management:
  - Primary: in-app UI (add/remove/list)
  - Optional: SMS commands from a trusted sender (`trusted add/remove/list`)
- Action target: call and ring stream volume.

## Versioning

App version is defined in `app/build.gradle.kts`:

- `versionCode`: integer, always increment on release
- `versionName`: human-readable semantic version

Current version:

- `versionCode = 2`
- `versionName = "0.2.0"`

The app also displays current version in UI status header.

## Upgrade Behavior (Trusted Senders)

Trusted senders are stored in `SharedPreferences` file `androbot_trusted_senders`.
On normal app upgrade (installing newer APK over existing app with same package name), trusted senders are preserved.

Guarantees in current implementation:

- Upgrade migration path exists in `TrustedSenderStore`.
- Migration normalizes/deduplicates existing senders.
- Migration does not reset/remove sender list.

Trusted senders are removed only if app data is cleared or app is uninstalled.

## Architecture Selection

Container platform is auto-detected by `scripts/compose.sh` from host architecture:

- `arm64`/`aarch64` host -> `linux/arm64`
- `x86_64`/`amd64` host -> `linux/amd64`

Compose services in `docker-compose.ci.yml` use `${ANDROBOT_PLATFORM}`.

Optional override:

```bash
ANDROBOT_PLATFORM=linux/amd64 make test
ANDROBOT_PLATFORM=linux/arm64 make test
```

## Convenience Commands (Recommended)

Use `make` targets for day-to-day work.
All `gradle` and `adb` commands run from Docker containers.
Compose is resolved by `scripts/compose.sh` using:

- local `docker compose` plugin if available, or
- `docker/compose` container fallback

```bash
make doctor
make build
make install
make deploy
make test-unit
make test-device
make test
make ci
```

Quick loop:

```bash
make quick
```

## Quick Start

1. Clone/open this project.
2. Validate toolchain:

```bash
make doctor
```

3. Build and install:

```bash
make build
make install
```

4. Open the app and configure trusted senders in UI:
   - enter phone number
   - tap `Add`
   - use `Remove` as needed
   - trusted list is shown on screen

5. Send SMS from a trusted number, e.g. `volume max`.

## Build

Prerequisites:

- Docker

Build debug APK:

```bash
make build
```

## Install

Install to Docker emulator:

```bash
make install
```

Deploy to real phone over adb TCP (Docker adb):

```bash
PHONE_IP=192.168.1.50 PHONE_PORT=5555 make deploy
```

## Deploy to Real Phone

### Prerequisites

- Android phone with USB cable
- Developer options enabled on phone
- USB debugging enabled on phone
- `adb` available on host machine (`android-platform-tools`)

### 1. Enable developer mode and USB debugging

On phone:

- `Settings -> About phone -> Build number` (tap 7 times)
- `Settings -> Developer options -> USB debugging` (enable)

### 2. Build APK

```bash
make build
```

APK output:

- `app/build/outputs/apk/debug/app-debug.apk`

### 3. Connect and verify phone

```bash
adb devices
```

Accept the RSA fingerprint prompt on phone if shown.

### 4. Install APK

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Alternative via Docker adb (TCP mode):

```bash
PHONE_IP=<PHONE_IP> PHONE_PORT=5555 make deploy
```

### 5. First app setup on phone

1. Open `Androbot`
2. Grant SMS permission when requested
3. Add first trusted sender in UI

### 6. Verify automation

Send SMS from trusted number:

- `volume max`

### Optional: wireless ADB

After first USB connection:

```bash
adb tcpip 5555
adb connect <PHONE_IP>:5555
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Then you can deploy using Docker adb:

```bash
PHONE_IP=<PHONE_IP> PHONE_PORT=5555 make deploy
```

### How to find phone IP

Use the phone's Wi-Fi LAN IP (same network as your computer).

From Android UI (most devices):

1. `Settings -> Wi-Fi`
2. Tap connected network
3. Find `IP address` (example: `192.168.1.50`)

From adb (after USB connect):

```bash
adb shell ip -f inet addr show wlan0
```

Look for `inet <IP>/...`, for example `inet 192.168.1.50/24`.

## Configure

### First trusted sender (UI only)

On fresh install, trusted sender list is empty.
SMS commands are ignored until at least one trusted sender is added in the app UI.

### Trusted senders at runtime

Trusted senders are stored in app `SharedPreferences`.
You can add/remove/list them from UI anytime without rebuild/reinstall.

Optional remote management (trusted sender only):

- `trusted add <phone>`
- `trusted remove <phone>`
- `trusted list`

### App permissions

Declared in manifest:

- `android.permission.RECEIVE_SMS`
- `android.permission.MODIFY_AUDIO_SETTINGS`

### Command format

- `volume max`
- `volume min`
- `volume N` where `N` is integer `0..100`
- `trusted add <phone>`
- `trusted remove <phone>`
- `trusted list`

## Release

Create and publish a GitHub Release (tag + release notes + debug APK asset):

```bash
VERSION=0.2.0 make release
```

Requirements:

- Clean git working tree
- `gh auth login` completed
- Push access to `origin`

## Test

Unit tests:

```bash
make test-unit
```

Instrumentation tests:

```bash
make test-device
```

CI emulator job also runs SMS integration verification (`.ci/verify-sms-flow.sh`):

- installs debug APK
- configures first trusted sender via debug test receiver
- sends emulator SMS `volume max`
- asserts call/ring volumes reach max

All tests:

```bash
make test
```

Full local CI pipeline:

```bash
make ci
```

## Security Notes

- SMS sender identity can be spoofed in some networks/devices.
- Trusted-sender management SMS commands are accepted only from trusted numbers.
- On fresh install, SMS handling is disabled until first trusted sender is added in UI.

## Project Structure

- `app/src/main/java/com/androbot/app/` - app logic
- `app/src/main/res/` - UI resources
- `app/src/test/` - unit tests
- `app/src/androidTest/` - instrumentation tests
- `.github/workflows/android.yml` - GitHub Actions CI
- `scripts/dev.sh` - local command orchestrator (Dockerized tools)
- `scripts/gradlew.sh` - Gradle launcher in `ci` container
- `scripts/compose.sh` - Compose wrapper with arch detection
- `Makefile` - convenience targets

## License

This project is licensed under the MIT License. See `LICENSE`.
