# Lock

**Block distracting apps with a physical NFC tag.**

Lock turns any NFC tag into a physical switch for your focus: tap it to block your distracting apps, tap it again to unblock them. No willpower negotiation with a "disable blocking" button. Unlocking requires the physical tag, so leaving your tag in another room means leaving your distractions there too.

<p>
  <a href="https://play.google.com/store/apps/details?id=com.nathanb.lock">
    <img alt="Get it on Google Play" src="https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png" height="60">
  </a>
</p>

[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)
![Platform](https://img.shields.io/badge/platform-Android%2013%2B-brightgreen)
![Kotlin](https://img.shields.io/badge/Kotlin-100%25-purple)

## Features

- **NFC toggle**: pair one or more NFC tags; scanning a paired tag locks or unlocks your phone's distracting apps, even when the app isn't running
- **Profiles**: different app lists for different contexts (Work, Sleep, Focus...), including temporary and no-escape session types
- **Manual mode**: no tag? Lock and unlock from the app, with timed sessions
- **Session stats**: history, streaks, weekly charts and total focused time
- **Safety valves**: emergency unlocks (limited), a 5-hour session timeout, and uninstalling always restores access
- **Private by design**: no account, no network calls, everything stays on your device; JSON backup/export included
- **Localized**: French, English, German. Light and dark themes.

## How it works

1. An [Accessibility Service](https://developer.android.com/guide/topics/ui/accessibility/service) watches for app launches while a session is active
2. When a blocked app opens, Lock bounces you back to the home screen and shows a gentle overlay
3. Lock/unlock state is toggled by scanning a paired NFC tag (Reader Mode in the foreground, NDEF intent routing from the background; pairing writes an [AAR](https://developer.android.com/develop/connectivity/nfc/nfc#aar) to the tag so Android launches Lock automatically)

No device-admin tricks, no VPN, no root. The blocklist, sessions and settings live in a local Room database and DataStore.

## Tech stack

- 100% Kotlin, single-activity Jetpack Compose (Material 3, custom design system)
- Room + DataStore, Kotlin Coroutines/Flow
- No third-party runtime dependencies beyond AndroidX

## Building from source

```bash
git clone https://github.com/NathanLenias/lock-app.git
cd lock-app
```

**Fonts (required):** Lock uses the [Satoshi](https://www.fontshare.com/fonts/satoshi) typeface, which is free but not redistributable, so the font files are not in this repo. Download Satoshi from Fontshare and drop these five files into `app/src/main/res/font/`:

```
satoshi_light.otf
satoshi_regular.otf
satoshi_medium.otf
satoshi_bold.otf
satoshi_black.otf
```

Then build and install:

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

NFC features need a physical device with NFC hardware (min SDK 33 / Android 13).

## Support

Lock is free, without ads or tracking. If it helps you focus, you can [buy me a coffee](https://buymeacoffee.com/nathanpmlen) ☕

## Related projects

- [Foqos](https://github.com/awaseem/foqos): a great NFC-based app blocker for iOS

## License

[MIT](LICENSE)
