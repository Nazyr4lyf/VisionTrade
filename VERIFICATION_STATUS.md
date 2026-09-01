# VisionTrade — Verification Status

## Changes made in this remediation

- Added a deterministic local chart-vision parser for red/green candle extraction.
- Screenshot mode now requires three images in the order M15 → H1 → H4.
- Camera mode now uses the existing CameraX dependencies to capture M15 → H1 → H4.
- Missing/uncertain image data is never filled with demo values; the strategy receives incomplete data and returns WAIT.
- Removed the obsolete `createDemoAnalysis()` production method.
- Fixed the manual-entry `ChartAnalysis` construction so it matches the actual domain model. Three manually entered candles intentionally produce incomplete data and therefore WAIT.
- Added real VisionTrade launcher/adaptive icon assets from the supplied logo.
- Removed obsolete broad storage permissions; the document picker does not require them.
- Fixed history navigation so it does not create a permanent flow collector on every navigation.

## Important limitation

The local vision parser is intentionally conservative. It works from visible red/green candle rendering and does not fabricate absolute prices from an unreadable price axis. It can return WAIT for themes, screenshots, overlays, or layouts it cannot parse confidently.

## Build verification

A full Gradle/Android build could not be executed in this environment because the archive did not contain `gradle-wrapper.jar` and no system Gradle/Android SDK is available here. Therefore this package is **not being falsely labeled build-verified**.

Before deployment, run:

```bash
./gradlew clean test
./gradlew assembleDebug
```

and install the generated APK on a real Android device.
