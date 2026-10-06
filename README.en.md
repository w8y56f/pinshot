# PinShot

[简体中文](README.md) | English

<div align="center">
  <img src="docs/images/pinshot-icon.jpg" alt="PinShot app icon" width="180" />
</div>

**PinShot is an Android picture pinning tool.** Keep images or text floating above other apps so you can refer to them while you work.

## Screenshots

<div align="center">
  <img src="docs/images/pinshot-home.jpg" alt="PinShot home screen" width="360" />
</div>

## Features

- **Pin images:** Choose a photo, take a picture, or share an image from another app to PinShot. Adjust the crop and pin it on screen.
- **Pin text:** Turn clipboard text or text you enter into a floating note.
- **Move and resize:** Drag a pinned image to move it, or drag its lower-right handle to resize it proportionally. Images can be moved partly off-screen and dragged back later.
- **Image actions:** Double-tap a floating image to close it or all images, rotate it counterclockwise, save it to your gallery, or share it through Android's system share sheet.
- **Multiple pinned images:** Sharing another image adds a new floating image while keeping existing ones.
- **Sharing guide:** An in-app guide explains how to make PinShot available in the OriginOS image share sheet.

## How to use

### Pin an image from another app

1. Select an image in Gallery, a screenshot preview, or another app, then tap **Share**.
2. Choose **PinShot** in the share sheet.
3. A progress indicator appears while the image is processed, then the image floats over the source app. The first time, allow PinShot to **Display over other apps**.

You can also tap **Gallery** on the PinShot home screen to choose an image, or **Camera** to take one. Adjust the crop and tap ✓ to pin it.

### Pin text

- **Pin clipboard text:** Read text from the clipboard and pin it directly.
- **Pin input text:** Pin the text in the input field.
- **CTRL+V Paste:** Paste clipboard text into the input field.
- **Clear input:** Clear the input field.

### Control a floating image

- Drag the image with one finger to move it.
- Drag the lower-right handle to resize it proportionally.
- Double-tap the image to open its actions: close the current image, close all images, rotate, save, or share.
- Share another image to add an additional floating image.

## Requirements

- Android 11 (API 30) or later.
- Compile and target SDK: Android 16 (API 36).

## Build

Android Studio and JDK 17 are required. Open the project in Android Studio, wait for Gradle sync, then run the `app` configuration on a connected device.

You can also build a debug APK from the project root:

```bash
./gradlew :app:assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`.

## Permissions and privacy

PinShot needs the **Display over other apps** permission to show floating images and text. It reads images selected through Android's share or system photo picker flows. It does not require Accessibility, screen recording, or broad photo library access.

## Version

Current project version: **1.0.0**.
