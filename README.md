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
  - `call me back` (places callback to trusted sender and enables speaker mode)
- Trusted sender management:
  - Primary: in-app UI (add/remove/list)
  - Optional: SMS commands from a trusted sender (`trusted add/remove/list`)
- Action target: call, ring, and media stream volume.

## Quick Start

1. Clone/open this project.
2. Validate toolchain:

```bash
make doctor
```

3. Build release APK:

```bash
make build-release
```

4. Choose deployment target:

Real phone (USB adb):

```bash
adb install -r app/build/outputs/apk/release/app-release-unsigned.apk
```

Emulator/dev flow (debug build):

```bash
See `Debug Build` section below.
```

5. Open the app and configure trusted senders in UI:
   - enter phone number
   - tap `Add`
   - use `Remove` as needed
   - trusted list is shown on screen

6. Send SMS from a trusted number, e.g. `volume max`.

## Build

Prerequisites:

- Docker
- adb (`android-platform-tools`) installed locally

Build notes:

- `make` targets run Gradle from Docker containers and adb from host.
- Compose is resolved by `scripts/compose.sh` (local `docker compose` plugin, or `docker/compose` container fallback).
- Container platform is auto-detected from host arch (`arm64` -> `linux/arm64`, `x86_64` -> `linux/amd64`).
- Gradle builds default to `linux/amd64` containers (including Apple Silicon) for reliable `aapt2`.
- Gradle container heap defaults to `-Xmx1536m`; override with `ANDROBOT_GRADLE_JVMARGS`.
- Optional override example: `ANDROBOT_PLATFORM=linux/amd64 make build-release`

Validate tooling:

```bash
make doctor
```

Build release APK (default):

```bash
make build-release
```

Build release AAB (Google Play artifact):

```bash
make build-bundle
```

Release APK output:

- `app/build/outputs/apk/release/app-release-unsigned.apk`
- `app/build/outputs/bundle/release/app-release.aab`

## Debug Build

Use this for emulator/dev loops.

Build debug APK:

```bash
make build
```

## Install

Install to Docker emulator:

```bash
make install
```

Manual emulator control:

```bash
make emu-up
make emu-logs
make emu-down
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

Use the `Build` section above (`make build-release`).

APK output:

- `app/build/outputs/apk/release/app-release-unsigned.apk`

### 3. Connect and verify phone

```bash
adb devices
```

Accept the RSA fingerprint prompt on phone if shown.

### 4. Install APK

```bash
adb install -r app/build/outputs/apk/release/app-release-unsigned.apk
```

Alternative via local adb TCP (debug flow):

```bash
APK_PATH=<path-to-apk> PHONE_IP=<PHONE_IP> PHONE_PORT=5555 make deploy
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
adb install -r app/build/outputs/apk/release/app-release-unsigned.apk
```

Then you can deploy using local adb:

```bash
APK_PATH=<path-to-apk> PHONE_IP=<PHONE_IP> PHONE_PORT=5555 make deploy
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
- `android.permission.CALL_PHONE`

### Command format

- `volume max`
- `volume min`
- `volume N` where `N` is integer `0..100`
- `call me back`
- `trusted add <phone>`
- `trusted remove <phone>`
- `trusted list`

## Release

Release process is documented in `RELEASE.md`.

## Test

Unit tests:

```bash
make test-unit
```

Device tests:

```bash
make test-device
```

CI emulator job also runs SMS integration verification (`.ci/verify-sms-flow.sh`):

- installs debug APK
- configures first trusted sender via debug test receiver
- sends emulator SMS `volume max` from untrusted sender and verifies no volume change
- sends invalid command from trusted sender and verifies no volume change
- sends untrusted `call me back` and verifies call path is ignored
- sends emulator SMS `volume max`
- asserts media volume reaches max
- sends trusted `call me back` and verifies callback path is reached

CI emulator job also runs app lifecycle verification (`.ci/verify-app-lifecycle.sh`):

- installs debug APK
- adds a trusted sender
- kills app process and verifies trusted sender is preserved
- upgrades app in place (`adb install -r`) and verifies trusted sender is preserved
- uninstalls/reinstalls app and verifies trusted sender list is reset

All tests:

```bash
make test
```

Full local CI pipeline:

```bash
make ci
```

## Advanced Debug: Emulator UI in Browser

Start emulator container with browser-accessible UI:

```bash
make emu-up
```

Open:

- `http://localhost:6080`

Useful commands:

```bash
make emu-logs
make emu-down
```

Notes:

- This is useful for manual UI/SMS debugging against the same Docker emulator setup.
- Emulator install/deploy command for this containerized emulator remains:
  - `make install`

## Security Notes

- SMS sender identity can be spoofed in some networks/devices.
- Trusted-sender management SMS commands are accepted only from trusted numbers.
- On fresh install, SMS handling is disabled until first trusted sender is added in UI.

## Project Structure

- `app/src/main/java/com/androbot/app/` - app logic
- `app/src/main/res/` - UI resources
- `app/src/test/` - unit tests
- `app/src/androidTest/` - device tests
- `.github/workflows/android.yml` - GitHub Actions CI
- `scripts/dev.sh` - local command orchestrator (Dockerized tools)
- `scripts/gradlew.sh` - Gradle launcher in `ci` container
- `scripts/compose.sh` - Compose wrapper with arch detection
- `Makefile` - convenience targets

## License

This project is licensed under the MIT License. See `LICENSE`.
