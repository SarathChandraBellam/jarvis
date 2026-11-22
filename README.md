# J.A.R.V.I.S. HUD

An on-device personal assistant for Android (Kotlin + Jetpack Compose). It reads your SMS, notifications, calendar, call log and screen time, sorts them into an inbox (finance, bills, deliveries, travel…), and shows a daily briefing, a money view and an "Ask Jarvis" chat.

- **Tabs:** Home · Money · Inbox · Ask, plus Settings
- **AI:** OpenRouter (bring your own key, stored encrypted on device) or a fully local parser
- **Privacy:** optional redaction of numbers, account IDs etc. before anything is sent to the AI
- **Settings save automatically** — there is no Save button

## Install

Download the APK from [Releases](../../releases) and open it on your phone (allow "install unknown apps"). Requires Android 8.0+ (API 26).

**"App blocked to protect your device"?** Google Play Protect blocks sideloaded apps that ask for SMS or notification access when they're installed from a browser or file manager. Either install over USB with `adb install jarvis.apk`, or temporarily turn off *Play Store → Play Protect → ⚙ → Scan apps with Play Protect*, install, and turn it back on.

## Build

Requires JDK 17 and Android SDK platform 37.

```sh
./gradlew testDebugUnitTest   # unit tests (JVM, no emulator)
./gradlew assembleDebug       # debug APK
./gradlew assembleRelease     # release APK
```

Release signing is read from a properties file outside the repo
(`~/.android-keystores/jarvis-keystore.properties`, or `-PjarvisKeystoreProps=<path>`)
with `storeFile`, `storePassword`, `keyAlias`, `keyPassword`. Without it, the release APK is unsigned.
