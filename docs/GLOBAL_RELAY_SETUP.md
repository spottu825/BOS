# BOS Global Relay Setup

This is the global internet path. Local Wi-Fi still works at `http://PHONE-IP:8080`.

## What is implemented now

- Relay routes:
  - `GET /health`
  - `POST /api/phone/register`
  - `POST /api/phone/:publicId/frame`
  - `GET /api/phone/:publicId/control`
  - `GET /view/:publicId`
  - `GET /stream/:publicId`
  - `POST /control/:publicId`
- Android `GlobalRelayClient` uploads JPEG frames to the relay and polls controls.
- The phone app shows the global viewer URL after session start when the APK was built with `BOS_RELAY_URL`.
- Permanent pair/link: the phone keeps a permanent BOS identity in app storage. The relay hashes that identity into the same `/view/...` URL every time on the same hosted relay.

## What is still required

A global URL cannot work from only the APK. The relay must be hosted on public HTTPS first, for example:

```text
https://your-bos-relay.example.com
```

Then rebuild the APK with that URL.

## Build APK with relay URL

In GitHub repository settings, add a repository variable or secret:

```text
BOS_RELAY_URL=https://your-bos-relay.example.com
```

Then run the Android APK workflow. The workflow now passes `BOS_RELAY_URL` into Gradle.

Local example:

```powershell
./gradlew :app:assembleDebug -PBOS_RELAY_URL=https://your-bos-relay.example.com
```

## Install/update issue: Play Protect or app not installing

If Android says it cannot install until you uninstall the old BOS app, that is usually because the old APK and new APK were signed by different debug keys. Android blocks upgrading when the signature changes.

Fix options:

1. For testing: uninstall old BOS, then install the new APK.
2. For smooth updates: use one stable signing key for every build, so Android sees it as the same trusted app update.

Play Protect may still warn because this is a custom APK outside Play Store. That does not mean the app is a virus; it means it is not Play Store reviewed. Use a stable signing key and eventually publish/register properly if you want fewer warnings.

## Safety note

The global viewer URL is share-by-link. Anyone with the link can view/control while sharing is active. Only send it to trusted people.
