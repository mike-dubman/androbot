# Development

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

Release artifact output:

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
