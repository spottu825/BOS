# BOS

BOS is a local-first Android screen-sharing system for Android 10+.

## First release

- Android device can run as **Sender** or **Viewer**.
- Sender requests Android's normal screen-capture permission and shows a foreground notification while sharing.
- Viewers can connect with either the BOS Android app or a modern browser on the same local network.
- The sender displays a QR code, a copyable connection link, and a human-readable pairing code.
- A sender-created password is required before a viewer receives a stream.
- Designed so remote access can be added later without changing the user-facing pairing model.

## Connection model

### Local first

The media stream stays on the local Wi-Fi network where possible. WebRTC is the planned transport because it is designed for real-time, low-latency video.

Bluetooth may be used later for nearby-device discovery or pairing, but not as the primary screen-video connection: Wi-Fi is far more reliable for the stream.

### Permanent BOS identity versus a network address

A phone's local IP address is temporary. BOS will therefore keep a stable **BOS device identity** and pairing record separately from the current network route.

The final form of the user-requested permanent URL needs to be chosen before implementation. A browser-accessible local link can only work when its hostname resolves on the current network (for example, a `.local` name) or when a hosted BOS relay resolves it. A device identity can stay permanent even when the local route changes.

The app must never put the sharing password directly in a link or QR code. Instead, a link carries a non-secret device/session reference, and the viewer enters the password before it can join.

## Planned architecture

```
android-app/
  sender/                 Android screen capture, local signaling, session controls
  viewer/                 Android viewer experience
  shared/                 pairing, crypto, protocol models
web-viewer/               browser viewer client
protocol/                 versioned signaling and pairing message definitions
.github/workflows/        GitHub Actions for APK and web build artifacts (added when GitHub is connected)
```

### Key Android components

- Kotlin + Jetpack Compose
- MediaProjection API for user-approved screen capture
- Foreground service during an active share
- WebRTC for video transport
- Encrypted local storage for the sender's password and device identity
- QR code generation for pairing
- Network discovery / resolution layer, abstracted so local Wi-Fi and future remote relay both use the same pairing flow

## Reliability and privacy requirements

- Clear status for network changes, stopped screen permission, wrong password, disconnected viewer, and unavailable route.
- Explicit stop-sharing control in the app and notification.
- No ADB, USB connection, root access, or bypass of Android permissions.
- Protected apps/content may block capture or appear blank, as required by Android/DRM protections.
- No remote access service in the first build; remote capability is an intentional later phase requiring a hosted signaling/relay service and security review.

## Build status

The folder has been initialized with the project specification. Android SDK, Java, and Git were not found in the current development environment, so the runnable Android project will be created after the Android toolchain is installed or an existing installation path is provided.
