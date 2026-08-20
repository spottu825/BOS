# BOS WebRTC backup mode

## Decision

BOS v1 keeps **MJPEG as the stable default stream** because it is simple, local, and easy to debug.

WebRTC will be added later as an optional smoother transport, not as a replacement until it has been tested.

## Target stream modes

The browser viewer should eventually show:

- **Auto** — try WebRTC beta first, fall back to MJPEG if it fails.
- **MJPEG** — always use the stable `/stream` image stream.
- **WebRTC beta** — use WebRTC only and show a clear error if negotiation fails.

## Rules

- Do not remove MJPEG.
- Do not add a fake WebRTC button that does nothing.
- Do not make WebRTC the default until it is proven on real phones.
- WebRTC failures must not break the local URL, touch controls, or MJPEG stream.
- Touch/control endpoints stay HTTP-based first so both MJPEG and WebRTC modes can share the same controls.

## Implementation order

1. Confirm the current MJPEG + touch APK builds and shows the screen.
2. Add a stream-mode selector in the browser UI.
3. Add Ktor WebSocket signaling routes.
4. Add Android WebRTC sender pipeline.
5. Add browser RTCPeerConnection receiver.
6. Add timeout/fallback: if WebRTC is not connected quickly, keep MJPEG running.
7. Only after testing, make Auto the default.
