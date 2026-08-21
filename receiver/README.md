# BOS Receiver

Desktop helper for BOS.

## Current features

- Open a phone's local BOS URL in a new tab.
- Embed the phone's local BOS viewer inside the receiver page.
- Open an ADB terminal in another tab.
- Run ADB commands only from the local laptop at `127.0.0.1:9090`.
- Connect to a BOS relay WebSocket and enter a pairing code from a phone.

## Start on Windows

Double-click:

```text
start-receiver.bat
```

Then open:

```text
http://127.0.0.1:9090
```

## Start from terminal

```bash
cd receiver
npm install
npm start
```

## Local viewer

Use this with the current APK when phone and laptop are on the same Wi-Fi:

1. Start sharing in BOS APK.
2. Copy the phone URL, for example `http://192.168.1.20:8080`.
3. Paste it into BOS Receiver.
4. Click **Open below** or **Open in new tab**.

## ADB terminal

Click **Open ADB Terminal in new tab**.

Requirements:

- Android platform-tools installed on the laptop/PC.
- `adb` available on PATH.
- Phone has authorized USB or Wireless Debugging.

This receiver does not enable ADB by itself. It only gives a local browser UI for ADB after the phone/laptop have already been authorized.

## Global pairing panel

The receiver now has UI for:

- relay WebSocket URL,
- receiver ID,
- pairing code from phone,
- relay event log,
- test signal.

Honest status:

- Receiver side relay UI exists.
- Relay server code exists in `../relay`.
- Phone APK global mode is not integrated yet.
- Global screen viewing is not usable until the APK connects to the hosted relay and sends stream/control data through it.
