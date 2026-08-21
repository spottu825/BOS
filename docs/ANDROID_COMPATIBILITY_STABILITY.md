# Android / Chromebook compatibility and stability

BOS targets Android 10+ (`minSdk 29`) and is designed to run on Samsung, Vivo, Oppo/Realme/OnePlus, Google Pixel, tablets, and Chromebooks that support Android apps.

## What was added for compatibility

- Phone/tablet/Chromebook friendly manifest:
  - does not require touchscreen hardware, so ChromeOS mouse/trackpad devices are allowed
  - supports small, normal, large, and xlarge screens
  - app activity is resizeable for tablets, ChromeOS, split-screen, and Samsung DeX
- Safe and full APK outputs:
  - `BOS-safe-debug-apk` for client view-only sharing
  - `BOS-full-debug-apk` for advanced control testing
- UI has scroll support for small phones.
- Browser viewer fits the phone screen preview instead of overflowing.
- Added `Battery / brand help` button to open Android battery settings.

## Important Android limitation

Screen sharing permission cannot be permanently enabled in Settings. Android requires the user to tap the system screen-sharing popup each time sharing starts.

Normal flow:

1. Open BOS Screen Share.
2. Tap `Start local session`.
3. Android popup appears.
4. Tap `Start now`.
5. URL appears.

## Brand-specific stability notes

Some Android brands kill background apps aggressively. If sharing stops when the screen locks or app goes to background, open `Battery / brand help` and set BOS to unrestricted / allow background usage.

### Samsung

- Settings → Apps → BOS Screen Share → Battery → Unrestricted
- Disable sleeping/deep sleeping for BOS if available.
- On DeX/tablets/Chromebooks, keep BOS visible during first test.

### Vivo / iQOO

- Battery → Background power consumption management → allow BOS
- Permission manager → Autostart/background if available

### Oppo / Realme / OnePlus

- Battery → App battery management → allow background activity
- Auto launch / background launch: allow BOS if available

### Google Pixel / stock Android

- Settings → Apps → BOS Screen Share → App battery usage → Unrestricted

### Chromebooks

- Requires ChromeOS with Android apps enabled.
- Developer mode is not always required for Android APK install, but many Chromebooks require Linux/ADB or developer options for sideloading.
- Screen capture inside Android-on-ChromeOS may behave differently by device; local URL/browser viewing should still be tested.

## Best stable setup

For clients:

- Use `BOS-safe-debug-apk`.
- Keep it view-only.
- Use visible notification and clear Start/Stop flow.
- Avoid Accessibility/remote control unless the user intentionally installs full build.

For your own testing:

- Use `BOS-full-debug-apk`.
- Enable Accessibility only if you need touch/control.
- Use unrestricted battery mode.
