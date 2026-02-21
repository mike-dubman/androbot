# Androbot

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

## Test

Unit tests:

```bash
make test-unit
```

Instrumentation tests:

```bash
make test-device
```

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
