# BOS normal-user connection plan

## Goal

BOS should feel normal:

- install APK on phone
- open receiver on laptop/browser
- pair once with a short code
- tap Start Sharing
- no ADB required
- no router port forwarding
- no public IP typing
- no MAC address setup
- no developer tools

Android will still require normal user-visible permissions:

- screen capture permission
- Accessibility permission if remote touch is enabled
- brightness/write-settings permission if brightness buttons are enabled
- foreground notification while sharing

Those are normal Android permission screens, not developer setup.

## The way out: BOS Relay

For remote/internet use, the simplest user experience requires a BOS relay/signaling service.

Both devices connect **outward** to the relay:

```text
Phone APK  ──outbound internet──► BOS Relay ◄──outbound internet──  Laptop/Web Receiver
```

This avoids:

- router settings
- port forwarding
- public IP typing
- CGNAT problems
- mobile-data inbound blocking
- ADB pairing

This is how normal remote apps work. They do not ask users to open router ports; they use a relay/signaling service.

## Normal user flow

### First time

1. User installs BOS APK on phone.
2. User opens `https://bos-viewer.example` or installs the BOS receiver app on laptop.
3. Phone shows a short pairing code, for example `482-913`.
4. User types the code on laptop.
5. Phone asks: “Pair with this laptop?”
6. User taps Allow.
7. Pairing is saved permanently.

### Every time after

1. Phone opens BOS.
2. User taps **Start Sharing**.
3. Laptop shows the phone as online.
4. User clicks the phone.
5. Screen opens.

If phone and laptop are on the same Wi‑Fi, BOS can use local direct mode for speed.
If they are away from each other, BOS uses relay mode.

## Connection modes behind the scenes

### Mode 1 — Local direct

Used when both devices are on same Wi-Fi.

```text
Laptop/browser → http://phone-ip:8080
```

Fast and free. No internet server needed after local connection is found.

### Mode 2 — Relay signaling + direct peer

Used when internet is available.

1. Phone and laptop connect to BOS Relay.
2. Relay confirms they are paired.
3. Relay helps them attempt direct peer connection.
4. If direct connection works, video/control goes peer-to-peer.

### Mode 3 — Full relay fallback

Used when direct peer connection fails because of NAT, CGNAT, mobile data, school Wi-Fi, etc.

1. Phone sends stream/control data through relay.
2. Laptop receives it through relay.

This is the most reliable, but uses server bandwidth.

## What the user sees

The user should not see technical words like NAT, CGNAT, STUN, TURN, port forwarding, or MAC.

Phone app should show:

- **Start Sharing**
- **Enable Touch Control**
- **Enable Brightness Control**
- **Paired Devices**
- **Connection: Local / Internet / Relay**
- **Stop Sharing**

Laptop receiver should show:

- paired phones
- online/offline status
- connect button
- screen viewer
- control buttons

## Free/simple implementation options

### Option A — Cloudflare Worker relay for signaling

Good for pairing and presence. Very cheap/free to start. Not ideal for heavy video relay.

### Option B — Node.js relay on a small VPS

Best control. Can handle WebSocket signaling and maybe MJPEG relay for testing.

### Option C — Firebase/Supabase signaling

Easy for pairing/presence and message exchange. Video should still be direct or use a proper relay.

### Option D — WebRTC with public STUN and optional TURN

Best long-term smooth streaming. Needs fallback because not all networks allow direct peer-to-peer.

## Recommended BOS product plan

1. Keep current same-Wi‑Fi local mode as the free fallback.
2. Add a simple pairing screen with a 6-digit code.
3. Build a web/desktop receiver that stores paired devices.
4. Add a small BOS Relay for presence and pairing.
5. Add remote connection through relay.
6. Add WebRTC Auto mode:
   - try direct WebRTC
   - if it fails, use relay/fallback
   - keep MJPEG as stable local fallback

## Key decision

If BOS must work away from home with no developer/router setup, then a relay/signaling service is required.

There is no fully normal-user way for a phone on random Wi-Fi/mobile data to accept inbound internet connections without either:

- a relay/server, or
- router/developer/manual setup.

So the normal solution is: **BOS Relay + one-time pairing code**.
