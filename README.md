# CV Lab

An Android 10+ offline computer-vision demo for prerecorded video.

## Features
- Select a local video through Android's document picker.
- Samples up to 24 frames from the video.
- Runs on-device ML Kit object detection and classification.
- Draws detected object boxes and labels over each sampled frame.
- Keeps processing on-device; no video upload or account is required.

This is a general computer-vision learning tool. It does not inspect another app's screen, expose hidden game state, or provide a live-game overlay.

## Build
Requirements: JDK 17, Android SDK 35, and Gradle 8.9.

```bash
gradle assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`.

GitHub Actions builds the debug APK on pushes to `main`, pull requests, and manual workflow dispatch.
