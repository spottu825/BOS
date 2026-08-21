# Play Protect and BOS APK builds

Google devices may warn about BOS because the full app asks for sensitive permissions:

- screen capture / MediaProjection
- Accessibility remote control
- write settings for brightness
- wake/lock controls
- sideloaded debug APK outside Play Store

That combination looks dangerous to Android even when the user requested it. BOS should not try to bypass Play Protect or hide what it does.

## New APK outputs

GitHub Actions now builds two APKs:

1. `BOS-safe-debug-apk`
   - screen sharing only
   - no Accessibility remote-control service
   - no write-settings brightness permission
   - should trigger fewer warnings
   - best APK to send to a normal client who only needs to watch

2. `BOS-full-debug-apk`
   - screen sharing plus remote control features
   - includes Accessibility and advanced controls
   - more likely to trigger Play Protect warnings
   - best only for your own device/testing

## Install/update issue

If Android says you must uninstall the old app, the old APK and new APK were signed with different keys.

GitHub Actions now caches `~/.android/debug.keystore` to keep the same debug signing key after the cache is established. You may still need to uninstall once, then future GitHub builds should update more cleanly.

## To reduce warnings further later

- Use a stable release signing key instead of debug signing.
- Publish through Google Play internal testing or closed testing.
- Keep client builds view-only.
- Show clear disclosure before screen sharing starts.
- Keep the foreground notification active while sharing.
