# BOS

BOS is a local-first Android screen-sharing and remote-control app for Android 10+.

## Current v1 direction

BOS uses the phone itself as a small local web server. After the sender starts a session, another device on the same Wi‑Fi opens:

```text
http://<phone-ip>:8080
```

The browser enters the BOS password, then receives a live MJPEG screen stream and a control panel.

## Implemented in the current source

- Android sender app with a scrollable UI for small phones.
- Local browser URL at `http://<phone-ip>:8080`.
- Password gate before viewer access.
- MediaProjection foreground capture service with persistent notification.
- MJPEG screen streaming endpoint at `/stream`.
- Browser viewer page with fullscreen, touch, swipe, volume, brightness, wake, lock, back, home, recents, notifications, and power-menu buttons.
- Remote touch/control through an Android Accessibility Service that the sender must enable manually in Android Settings.
- Brightness control requires Android's separate “modify system settings” permission.
- Unsupported controls, such as shutdown on normal non-rooted phones, are shown disabled.
- GitHub Actions workflow builds a downloadable debug APK artifact.

## Why MJPEG first

WebRTC is still a good future high-performance transport, but it requires signaling, SDP, ICE, native WebRTC, and browser peer-code all to work together. For v1, MJPEG is much simpler and more testable:

- Android captures frames with MediaProjection + VirtualDisplay + ImageReader.
- The app encodes frames as JPEG.
- The browser displays them with a normal `<img src="/stream">`.
- Touch and buttons use simple authenticated HTTP endpoints.

Expected tradeoff: MJPEG uses more Wi‑Fi bandwidth and CPU than WebRTC, but it is easier to make reliable on a same-Wi‑Fi local network.

## Android limits

- Screen-capture permission must be approved through Android. BOS cannot bypass this.
- Accessibility must be enabled by the sender before remote touch works.
- Brightness requires modify-system-settings permission.
- File manager access must use Android's user-approved file/folder picker.
- Actual shutdown is unavailable on normal phones unless the device is rooted, device-owner managed, or controlled through an authorized ADB/device-management bridge.
- Mobile-data hosting generally will not accept incoming browser connections because carriers use NAT/firewalls; same-Wi‑Fi is the v1 target.

## Build

Push to GitHub and download the `BOS-debug-apk` artifact from Actions.

```bat
git add .
git commit -m "Add MJPEG screen stream and browser controls"
git push
```
