# BOS local-first architecture

## Confirmed link behavior

Each sender device has a durable, non-secret **BOS identity**. Pairing is initiated through a BOS QR code or BOS app identity. A browser does **not** use that permanent identity directly. Instead, after a local pairing/session begins, BOS generates a short-lived browser link and QR code for the current network session.

Example flow:

1. Sender creates its BOS identity once and chooses a sharing password.
2. A BOS viewer scans the permanent BOS QR code and saves the sender as a known device.
3. When the sender starts a share, it creates a short-lived local session.
4. The sender shows a browser URL/QR valid only for that session and local network.
5. Viewer enters the sender's password before joining.
6. Stopping a share invalidates the session link.

This meets the permanent-identity requirement without pretending that a local phone IP address is permanent.

## One device = local host

A sender phone can act as its own local service while sharing:

- It runs a foreground Android service only during an active share.
- It performs local session discovery/signaling and serves the browser viewer assets.
- The phone sends the screen stream directly to viewers on the local Wi-Fi network where possible.
- No BOS cloud server is required for this local-first release.
- The phone is not a permanent 24/7 public web host; Android battery, background, and network rules make that unreliable and undesirable.

A viewer phone uses its own CPU/network to decode and display the stream. This distributes work naturally, but it does not turn multiple phones into one reliable public hosting cluster.

## Encryption and access control

- WebRTC uses DTLS-SRTP encryption for media in transit.
- The pairing password is an access-control secret, not a URL parameter.
- The sender stores a salt and password verifier in Android encrypted storage; it should not keep or transmit the plaintext password after setup.
- A password-authenticated join flow proves the viewer knows the password before the sender reveals session credentials.
- QR codes contain only a non-secret BOS identity or a short-lived session reference.
- Future remote access will need authenticated signaling, certificate management, rate limits, and likely TURN relay infrastructure. It is intentionally outside the first local-only release.

## Android requirements

- Android 10+.
- User approves screen capture through MediaProjection for every Android-required capture grant.
- Foreground service and persistent notification remain visible while screen sharing is active.
- Android may block protected content or secure apps from appearing in the capture.

## Local connection routes

1. **BOS app viewer:** QR/permanent identity -> local discovery -> password -> WebRTC stream.
2. **Browser viewer:** current-session QR/link -> password -> WebRTC stream.
3. **Bluetooth:** later use only for discovery/pairing when desired; local Wi-Fi remains the preferred media route.

## Build pipeline (when source starts)

GitHub Actions will build the Android app on a hosted Linux runner and upload an APK as a downloadable artifact on each push or manual run. The user's computer does not need to remain powered on for that build after the source has been pushed.
