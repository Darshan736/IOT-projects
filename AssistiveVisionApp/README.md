# Assistive Vision: AI-Powered Navigation Aid

> **Note:** This is a student prototype developed for the IoT Projects curriculum. It is a proof-of-concept and not a commercial medical device.

## 📖 Project Overview
Visually impaired individuals face significant challenges in navigating unknown environments, often relying on white canes or guide dogs which have limitations in detecting chest-level or dynamic obstacles. 

**Assistive Vision** is an Android-based computer vision system that serves as a "digital eye". It leverages the ESP32-CAM module and an Android smartphone to provide real-time environment understanding. By processing video feeds through a YOLO Mobile architecture, the app identifies objects (people, obstacles), estimates their distance, and provides multi-modal feedback (auditory and haptic) to ensure safe navigation.

## ✨ Key Features
*   **Real-Time Object Detection**: Utilizes TensorFlow Lite (YOLO) to identify persons and generic obstacles with low latency.
*   **Distance Estimation**: fusing camera data with ultrasonic sensor readings to calculate precise proximity.
*   **Auditory Feedback**: Text-to-Speech (TTS) engine announces detected objects and their relative position.
*   **Haptic Alerts**: Progressive vibration feedback warning the user of immediate collision risks.
*   **AI Interpretation (Experimental)**: Integration with Groq LLM to provide natural language scene descriptions.

## 📥 Download & Install
The application is available as a pre-compiled APK for testing purposes.

### Option 1: Direct APK Download
1.  **Download**: [Click here to download app-debug.apk](apk/app-debug.apk) *(View file > Download)*
2.  **Transfer**: Move the file to your Android device via USB or download directly on the phone.
3.  **Install**: Open the file and allow "Install from unknown sources" if prompted.

### Option 2: Install via ADB
If you have Android Platform Tools installed:
```bash
adb install apk/app-debug.apk
```

## 🛠️ Build from Source
To explore the code or contribute, follow these steps:

### Prerequisites
*   Android Studio Ladybug (or newer)
*   Java Development Kit (JDK) 11 or 17
*   Physical Android Device (Minimum SDK 24, Android 7.0)

### Steps
1.  **Clone the Repository**
    ```bash
    git clone https://github.com/Darshan736/IOT-projects.git
    ```
2.  **Open in Android Studio**
    *   Launch Android Studio -> File -> Open
    *   Select the `IOT-projects/AssistiveVisionApp` folder.
3.  **Sync Gradle**
    *   Allow the project to sync dependencies.
4.  **Run**
    *   Connect your Android device via Debugging Mode.
    *   Click the **Run** (▶) button.

## 📁 Project Structure
```text
AssistiveVisionApp/
├── app/
│   ├── src/main/java/   # Kotlin Source Code (Detection Logic, UI)
│   ├── src/main/assets/ # TFLite Models (yolov8n.tflite)
│   └── src/main/res/    # UI Resources (Layouts, Strings)
├── apk/                 # Pre-built binaries
└── gradle/              # Wrapper files
```

## 🤝 Acknowledgments
*   **YOLOv8**: For the efficient object detection architecture.
*   **TensorFlow Lite**: For enabling on-device inference.
*   **Espressif**: For the ESP32-CAM hardware platform.

---
*Created by Darshan • 2026*
