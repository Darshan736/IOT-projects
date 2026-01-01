# Assistive Vision App for Visually Impaired

## Overview
The **Assistive Vision App** is an Android application designed to aid visually impaired users by providing real-time object detection and distance estimation using an ESP32-CAM module. This project acts as a "digital eye," offering auditory feedback and haptic warnings to enhance navigation safety.

## Features
- **Real-Time Object Detection**: Uses a YOLO-based model (via TFLite) to identify objects such as persons, obstacles, and more.
- **Distance Estimation**: Integrates with hardware sensors to measure distance to detected objects.
- **Auditory Feedback**: Text-to-Speech (TTS) announcements for detected objects and their proximity.
- **Haptic Alerts**: Vibration feedback when obstacles are critically close.
- **Low Latency**: Optimized for real-time performance on standard Android devices.

## How It Helps Blind Users
By combining computer vision with simple auditory and tactile cues, this app allows blind or visually impaired individuals to perceive their surroundings more effectively. It helps in identifying hazards, recognizing people, and navigating spaces with greater confidence.

## Installation (APK)
A pre-built debug APK is included in this repository.

1.  **Locate the APK**: Go to `apk/app-debug.apk`.
2.  **Enable USB Debugging**: On your Android phone, enable Developer Options and USB Debugging.
3.  **Install via ADB**:
    Connect your phone and run:
    ```bash
    adb install apk/app-debug.apk
    ```
    *Alternatively, transfer the APK file to your phone and install it manually.*

## Build from Source
To build the app yourself:

1.  **Clone the Repository**:
    ```bash
    git clone https://github.com/Darshan736/IOT-projects.git
    ```
2.  **Open in Android Studio**:
    - Launch Android Studio.
    - Select **Open** and navigate to `IOT-projects/AssistiveVisionApp`.
3.  **Sync & Build**:
    - Wait for Gradle to sync (Internet required).
    - Connect your device.
    - Click **Run** (Green Play button).

*Note: This is a student prototype and is not available on the Play Store.*
