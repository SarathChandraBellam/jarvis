# J.A.R.V.I.S. HUD

An on-device, Iron Man–style HUD for Android (Kotlin + Jetpack Compose). It reads your SMS, notifications, calendar, call log and screen time, sorts them into "intel" (finance, bills, deliveries, travel…), and shows a daily briefing, a finance view and an "Ask Jarvis" chat.

- **Tabs:** HUD · FINANCE · INTEL · ASK, plus Settings
- **Finance:** month-to-date spend vs the same point last month, daily average and month-end pace, where the money goes by category, top merchants, when you spend (time of day, weekday vs weekend), recurring payments and money in
- **No double counting:** a payment reported by bank SMS, the bank app and a UPI app is merged into one transaction (matched by UPI/bank reference, or same amount and account within 10 minutes from different channels)
- **AI:** OpenRouter (bring your own key, stored encrypted on device) or a fully local parser
- **Privacy:** optional redaction of numbers, account IDs etc. before anything is sent to the AI
- **Demo mode:** runs on fake data, so no permissions are needed to try it

## Install

Download `jarvis-v1.0.1.apk` from [Releases](../../releases) and open it on your phone (allow "install unknown apps"). Requires Android 8.0+ (API 26).

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
