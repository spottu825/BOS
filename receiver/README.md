# BOS Receiver

Desktop helper for BOS.

Current features:

- Open a phone's local BOS URL in a browser tab.
- Open an ADB terminal in another tab.
- Run ADB commands only from the local laptop at `127.0.0.1:9090`.

## Run

```bash
cd receiver
npm install
npm start
```

Open:

```text
http://127.0.0.1:9090
```

## ADB terminal

Click **Open ADB Terminal in new tab**.

Requirements:

- Android platform-tools installed on the laptop/PC.
- `adb` available on PATH.
- Phone has authorized USB or Wireless Debugging.

This receiver does not enable ADB by itself. It only gives a local browser UI for ADB after the phone/laptop have already been authorized.

## Global URL status

A global URL is not active yet. It requires:

1. hosted BOS relay,
2. APK global-mode connection to that relay,
3. receiver/web viewer connected to the same relay,
4. pairing/security between phone and receiver.

Current global relay server code is in `../relay`, but the APK is not integrated with it yet.
