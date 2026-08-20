# BOS Relay

This is the first backend component for BOS global mode.

It lets a phone and receiver connect outbound to a shared WebSocket relay, pair with a one-time code, and forward signaling/control messages between paired devices.

## Run locally

```bash
cd relay
npm install
npm start
```

Health check:

```text
http://localhost:8787/health
```

WebSocket endpoint:

```text
ws://localhost:8787/ws
```

## Message flow

### Phone connects

```json
{ "type": "hello", "role": "phone", "deviceId": "phone_abc", "name": "My phone" }
```

### Phone creates pairing code

```json
{ "type": "create_pair_code" }
```

Relay replies:

```json
{ "type": "pair_code", "code": "123456", "expiresInSeconds": 300 }
```

### Receiver pairs

```json
{ "type": "hello", "role": "receiver", "deviceId": "receiver_abc", "name": "My laptop" }
{ "type": "pair_with_code", "code": "123456" }
```

### Forwarding after pair

```json
{ "type": "signal", "to": "phone_abc", "payload": { "hello": true } }
{ "type": "control", "to": "phone_abc", "payload": { "action": "tap", "x": 0.5, "y": 0.5 } }
```

## Current status

Implemented:

- health check
- WebSocket relay
- phone/receiver hello
- one-time pairing code
- in-memory paired-device records
- presence updates
- paired message forwarding

Not implemented yet:

- persistent database
- account system
- end-to-end encryption
- APK integration
- browser receiver UI
- video relay
- WebRTC signaling integration

## Hosting

For normal-user global mode, this server must be hosted somewhere public, for example:

- VPS
- Render/Fly/Railway-style service
- home server behind Cloudflare Tunnel for testing

The Android APK and receiver must connect outbound to this relay.
