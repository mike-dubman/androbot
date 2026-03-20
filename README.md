# Androbot
[![Android CI](https://github.com/mike-dubman/androbot/actions/workflows/android.yml/badge.svg?branch=main)](https://github.com/mike-dubman/androbot/actions/workflows/android.yml)

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
  - `wifi reset`
  - `wifi on`
  - `wifi off`
  - `data reset`
  - `data off`
  - `data on`
  - `call me back` (places callback to trusted sender and enables speaker mode)
  - `update software` (opens app and runs OTA update check)
  - `info` (replies by SMS with current Androbot version and supported commands)
- Trusted sender management:
  - Primary: in-app UI (add/remove/list)
  - Optional: SMS commands from a trusted sender (`trusted add/remove/list`, `sms forwarder on/off`)
- SMS forwarding mode:
  - Enabled by trusted SMS command: `sms forwarder on`
  - Disabled by trusted SMS command: `sms forwarder off`
  - Forwards regular incoming SMS to the trusted sender who most recently enabled forwarding
- Action target: call, ring, and media stream volume.
- Network toggles are best-effort and may be ignored by modern Android security policies.

## Quick Start

### Prerequisites

- Android phone with USB cable
- Developer options enabled on phone
- USB debugging enabled on phone
- `adb` available on host machine (`android-platform-tools`)

### 1. Enable developer mode and USB debugging

On phone:

- `Settings -> About phone -> Build number` (tap 7 times)
- `Settings -> Developer options -> USB debugging` (enable)

### 2. Download release APK

Download the latest release APK asset from:

- `https://github.com/mike-dubman/androbot/releases`

Recommended asset names:

- `androbot-release-v<version>.apk`
- `androbot-release-latest.apk` (stable alias)

`curl` (auto-download latest release APK):

```bash
curl -fL -o androbot-release-latest.apk \
  https://github.com/mike-dubman/androbot/releases/latest/download/androbot-release-latest.apk
```

### 3. Connect and verify phone

Connect the phone to your computer using a USB data cable.

```bash
adb devices
```

Accept the RSA fingerprint prompt on phone if shown.

### 4. Install APK

```bash
adb install -r androbot-release-latest.apk
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
adb install -r androbot-release-latest.apk
```

Then you can deploy using local adb:

```bash
adb install -r androbot-release-latest.apk
```

## In-App Upgrade (OTA)

- Tap `Check update` in app UI.
- App fetches metadata from:
  - `https://github.com/mike-dubman/androbot/releases/latest/download/androbot-update.json`
- App downloads release APK, verifies SHA-256 checksum, then launches installer for in-place upgrade.
- On Android 8+, allow `Install unknown apps` for Androbot when prompted.

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
- `sms forwarder on`
- `sms forwarder off`

When SMS forwarding is enabled, Androbot forwards regular incoming SMS messages to the trusted sender
who most recently enabled forwarding. Command/control SMS messages are not forwarded. This requires
`SEND_SMS` permission.

### App permissions

Declared in manifest:

- `android.permission.RECEIVE_SMS`
- `android.permission.SEND_SMS`
- `android.permission.MODIFY_AUDIO_SETTINGS`
- `android.permission.CALL_PHONE`
- `android.permission.CHANGE_WIFI_STATE`
- `android.permission.INTERNET`
- `android.permission.REQUEST_INSTALL_PACKAGES`

### Command format

- `volume max`
- `volume min`
- `volume N` where `N` is integer `0..100`
- `wifi reset`
- `wifi on`
- `wifi off`
- `data reset`
- `data off`
- `data on`
- `call me back`
- `update software`
- `info`
- `trusted add <phone>`
- `trusted remove <phone>`
- `trusted list`
- `sms forwarder on`
- `sms forwarder off`

## Release

Release process is documented in `RELEASE.md`.

## Security Notes

- SMS sender identity can be spoofed in some networks/devices.
- Trusted-sender management SMS commands are accepted only from trusted numbers.
- On fresh install, SMS handling is disabled until first trusted sender is added in UI.

## License

This project is licensed under the MIT License. See `LICENSE`.

## Development and Simulator

Build, debug build, install, test, project structure, and emulator browser-UI debug are documented in `Development.md`.
