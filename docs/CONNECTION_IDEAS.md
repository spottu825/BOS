# BOS connection ideas

## Main idea

BOS should not depend on one connection method. It should try the easiest safe path first, then fall back to stronger methods when the network is harder.

Recommended connection stack:

1. **Same Wi-Fi direct** — default local mode.
2. **Local pairing code** — one-time trusted pairing.
3. **Laptop receiver app** — better than only browser when we want remote features.
4. **Relay/signaling server** — reliable internet mode.
5. **Direct router/public-IP mode** — advanced/optional.
6. **Bluetooth** — discovery/pairing helper, not main video transport.
7. **Wireless ADB mode** — advanced power-user control helper, optional.

## 1. Same Wi-Fi direct mode

Best for first working version.

Flow:

1. Phone starts BOS.
2. User taps **Start sharing**.
3. Phone shows `http://<phone-ip>:8080`.
4. Laptop/browser opens that address.
5. Browser shows screen and controls.

Pros:

- No account.
- No cloud.
- No router setup.
- Very simple.
- Works fully inside the APK.

Cons:

- Only works when both devices are on the same network.
- Guest Wi-Fi/client isolation can block it.
- Phone IP may change.

Use this as the stable fallback forever.

## 2. One-time pairing code

This makes BOS feel permanent without relying on phone IP.

Flow:

1. Phone shows a pairing code like `482-913`.
2. Laptop receiver enters the code.
3. Phone and laptop exchange permanent BOS device IDs and public keys.
4. Both save each other as trusted devices.

Saved record should include:

- BOS device ID
- display name
- public key
- last known local IP
- last known network name, if Android allows it
- supported modes: MJPEG, WebRTC beta, relay, direct-router, ADB helper

After pairing, the laptop app can show:

- `Spxttu phone — nearby`
- `Spxttu phone — remote via relay`
- `Spxttu phone — offline`

## 3. Laptop receiver app

A desktop receiver app is better than only using Chrome because it can remember paired phones and support remote connection logic.

Recommended stack:

- Electron + Node.js for Windows first.
- Package `.exe` from GitHub Actions later.
- Built-in viewer window.
- Local discovery during pairing.
- Stored trusted devices.
- Can open the browser viewer internally.

Receiver app features:

- Find BOS phones on same Wi-Fi.
- Enter pairing code.
- Save trusted phones.
- Open local stream.
- Later connect through relay.
- Later support WebRTC beta.
- Later support optional Wireless ADB helper commands.

## 4. Relay/signaling server

This is my recommended internet/away-from-home method.

How it works:

1. Phone connects outbound to BOS relay.
2. Laptop receiver connects outbound to BOS relay.
3. Relay checks paired device IDs and public keys.
4. Relay helps them find each other.
5. If possible, phone and laptop connect peer-to-peer.
6. If not possible, relay forwards the stream/control messages.

Why this is best:

- Works on mobile data.
- Works behind CGNAT.
- Works on school/work Wi-Fi.
- No router port forwarding.
- No manual public IP setup.

Downside:

- Needs a server.
- If video is relayed, bandwidth cost increases.

Free/cheap ways to start:

- A small Node.js WebSocket relay on a free/cheap VPS.
- Cloudflare Tunnel for testing.
- Render/Fly.io/Railway style hosting for signaling only.
- Later replace with a stronger relay/TURN setup if needed.

## 5. Direct router/public-IP mode

This is possible but should be advanced.

Flow:

1. Phone/laptop checks router public IP.
2. Router forwards a port to the device.
3. Paired receiver connects to `public-ip:port` or DDNS name.

Problems:

- Many ISPs use CGNAT, so it will fail.
- Mobile data usually cannot receive inbound connections.
- Router port forwarding is confusing.
- Exposing phone service to internet is risky.

If added, it should show diagnostics:

- public IP detected
- port open/closed
- CGNAT likely/not likely
- UPnP available/unavailable
- direct mode safe/unsafe warning

## 6. Bluetooth pairing helper

Bluetooth should not carry the screen video. It is too slow for good screen streaming.

Good Bluetooth uses:

- Find nearby BOS devices.
- Exchange pairing code/public keys.
- Wake up pairing process.
- Help when phone IP is unknown.

Bad Bluetooth use:

- Main video stream.
- Large file transfer while screen casting.

## 7. Wireless ADB advanced helper

This can be an optional advanced mode for stronger controls.

Flow:

1. User enables Wireless Debugging in Android Developer Options.
2. User pairs receiver app with phone using Android's pairing code.
3. Receiver app can run supported ADB commands.

Can help with:

- stronger wake/lock controls
- app launch commands
- file operations in allowed shell areas
- diagnostics
- some device settings

Limits:

- Not beginner-friendly.
- May need re-pairing after changes/reboots.
- Cannot be silently enabled by BOS APK.
- Should never be required for normal screen viewing.

## My recommended final connection behavior

### Local screen

Button: **Start sharing**

Shows:

- local URL
- status: local Wi-Fi reachable or not
- touch control enabled/disabled
- brightness control enabled/disabled

### Receiver app

Shows saved phones:

- Nearby direct
- Remote via relay
- Last seen
- Needs permission on phone

### Connection priority

When user clicks a paired phone:

1. Try same-Wi-Fi direct URL.
2. If unavailable, try relay/signaling.
3. If relay can make peer-to-peer, use peer-to-peer.
4. If peer-to-peer fails, relay the stream if enabled.
5. If all fail, show a clear reason.

## Build order I recommend

1. Make current same-Wi-Fi MJPEG + touch reliable.
2. Add a simple pairing-code screen on phone.
3. Create a Windows receiver app with Electron.
4. Let receiver find/open phone on same Wi-Fi.
5. Save paired phone records.
6. Add relay signaling server.
7. Add remote mode.
8. Add WebRTC beta as smoother stream transport.
9. Add direct router mode only after relay works.

## Key rule

Pairing creates trust. IP address and MAC address do not create trust.
