# BOS remote pairing and receiver architecture

## Goal

BOS should work in two situations:

1. **Local mode:** phone and viewer are on the same Wi-Fi, using `http://<phone-ip>:8080`.
2. **Remote mode:** phone and laptop/receiver are away from each other but both have internet.

The user wants one-time local pairing, then permanent trusted connection later.

## Important correction

A device MAC address is not useful for internet connection routing.

- MAC addresses are visible only inside the same local network segment.
- Across the internet, routers/NAT hide local MAC addresses.
- Android apps also have restricted access to real hardware MAC addresses for privacy.

Instead, BOS should use:

- BOS device ID
- public/private key pair per device
- pairing code
- saved trusted device record
- optional router/public-IP endpoint information
- optional relay/signaling server address

## One-time pairing flow

Pairing can happen using any local trusted method:

- same Wi-Fi local URL
- Bluetooth discovery
- QR/manual code
- USB/ADB advanced setup, optional later

Suggested first pairing:

1. Phone starts BOS pairing mode.
2. Phone shows a short code, for example `482-913`.
3. Laptop receiver app discovers phone on same Wi-Fi or user opens `http://<phone-ip>:8080/pair`.
4. User enters the phone code into laptop receiver.
5. Phone and laptop exchange:
   - permanent BOS device IDs
   - public keys
   - receiver name/device info
   - supported connection modes
6. Both save a trusted paired-device record.

After this, they do not need to re-enter the code unless pairing is reset.

## Remote connection models

### Option A — Direct router connection

Phone exposes a service through the home/router public IP.

Requirements:

- router port forwarding, or UPnP/NAT-PMP if available
- dynamic DNS if public IP changes
- TLS/certificate or public-key encryption
- firewall and security hardening

Problems:

- many ISPs use CGNAT, so inbound connections will not work
- mobile data usually blocks inbound connections
- router setup can fail or be unsafe if done badly
- exposing the phone directly to the internet is risky

Use only as advanced mode.

### Option B — Relay/signaling service

Both phone and laptop connect outbound to a BOS relay/signaling service.

Benefits:

- works behind NAT, CGNAT, mobile data, school/work Wi-Fi
- no router port forwarding
- no manual IP setup after pairing
- easier to secure with device keys

Costs:

- requires a server somewhere
- video relay can use bandwidth if direct peer-to-peer cannot work
- more code than local-only mode

This is the most reliable remote design.

### Option C — Laptop receiver as internet rendezvous

A laptop/desktop app runs as the receiver and can optionally expose itself via:

- public IP + port forwarding
- dynamic DNS
- cloud tunnel
- relay connection

Phone connects to the receiver using the saved paired identity and endpoint list. This is better than trying to make the phone a public internet server.

## Receiver app

The receiver can be built as a desktop app using Node.js/Electron or Java/Kotlin.

Recommended v1 receiver stack:

- Node.js + Electron for desktop UI
- local discovery during pairing
- saved paired-device database
- browser-like viewer panel
- HTTP/MJPEG viewer first
- future WebRTC receiver mode
- secure control channel for touch/buttons

Why Electron/Node first:

- easier to build Windows receiver quickly
- npm ecosystem is good for local networking, QR/pairing, and UI
- can later package as `.exe` using GitHub Actions

## Security model

Pairing creates trust; IP/MAC does not create trust.

Each device gets:

- random BOS device ID
- public/private key pair
- display name
- paired-device list

Connection messages should be signed or encrypted so random internet users cannot control the phone even if they find the endpoint.

Remote control must still require:

- screen capture permission from Android
- Accessibility permission for touch control
- visible foreground notification while active

## Recommended implementation order

1. Finish local MJPEG + touch mode.
2. Add permanent pairing records locally.
3. Build simple Windows receiver app with Node/Electron.
4. Pair phone and laptop on same Wi-Fi with a one-time code.
5. Use paired receiver app on local network first.
6. Add remote relay/signaling mode.
7. Add direct router/public-IP mode only as advanced/experimental.
8. Add WebRTC beta after the reliable local/receiver path works.

## Do not do

- Do not rely on MAC address for remote internet routing.
- Do not expose unauthenticated phone controls to the internet.
- Do not make router public-IP mode the only remote method.
- Do not remove local same-Wi-Fi mode; it remains the fallback.
