# BOS — Decisions to confirm

## Confirmed

| Area | Decision |
|---|---|
| App name | BOS |
| Platform | Android 10+ |
| Roles | Sender and viewer in the Android app; browser viewer also supported |
| Initial network scope | Local Wi-Fi first; architecture must allow remote access later |
| Access control | Sender-created password required for viewers |
| Developer setup | New local project, GitHub connected later |

## Still to decide: “permanent URL”

The requirement is **not** a raw local-IP address such as `http://192.168.x.x:8080`.

Choose the desired behavior:

1. **Permanent BOS identity, local route changes automatically**
   - Example user-facing form: `bos://BOS-9K2M-7Q`
   - Works best between BOS apps and QR codes.
   - A normal browser needs a companion browser URL/resolver.

2. **Friendly local browser hostname**
   - Example: `http://my-bos.local` or `http://my-bos.local:8080`
   - Can remain friendly and usually stable on compatible local networks, but `.local` name resolution is network/device dependent.

3. **Permanent internet URL**
   - Example: `https://bos.example/device-name`
   - Works outside the local network but requires a hosted BOS service, account/security design, and possibly a relay for media.

4. **Permanent install link plus a temporary local session link**
   - A permanent link opens/installs the viewer; a locally generated session URL carries the connection route.
   - Most reliable design for browser and app support without hosting a remote stream in the first release.

No URL or QR code will contain the actual sharing password. The viewer will always be challenged for it.
