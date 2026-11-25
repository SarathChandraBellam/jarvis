# ASTRA HUD

An on-device personal assistant for Android (Kotlin + Jetpack Compose). It reads your SMS, notifications, calendar, call log and screen time, sorts them into an inbox (finance, bills, deliveries, travel…), and shows a daily briefing, a money view and an "Ask ASTRA" chat.

- **Tabs:** Home · Money · Inbox · Ask, plus Settings
- **AI:** OpenRouter (bring your own key, stored encrypted on device) or a fully local parser
- **Privacy:** optional redaction of numbers, account IDs etc. before anything is sent to the AI
- **Settings save automatically** — there is no Save button
- **Welcome screen** on first launch to set your name and grant access
- **Inbox search**, pull-to-refresh, and a details sheet (tap any item: mark done, dismiss, share)
- **Money tab** in three views: *Overview* (month vs last month, budget, pace chart, recent payments), *Spending* (by category and on a calendar, tap to filter) and *Bills* (due soon, later, and what repeats monthly)
- **No double counting:** a payment reported by bank SMS, the bank app and a UPI app is merged into one (matched by UPI/bank reference, or same amount and account within 10 minutes from different channels)
- **Monthly budget** tracking on the Money tab
- **Tablet / landscape layout** with a side navigation rail

## Install

Download the APK from [Releases](../../releases) and open it on your phone (allow "install unknown apps"). Requires Android 8.0+ (API 26).

**"App blocked to protect your device"?** Google Play Protect blocks sideloaded apps that ask for SMS or notification access when they're installed from a browser or file manager. Either install over USB with `adb install astra.apk`, or temporarily turn off *Play Store → Play Protect → ⚙ → Scan apps with Play Protect*, install, and turn it back on.

## Build

Requires JDK 17 and Android SDK platform 37.

```sh
./gradlew testDebugUnitTest   # unit + end-to-end UI tests (Robolectric, no emulator needed)
./gradlew assembleDebug       # debug APK
./gradlew assembleRelease     # release APK
```

Release signing is read from a properties file outside the repo
(`~/.android-keystores/jarvis-keystore.properties`, or `-PastraKeystoreProps=<path>`)
with `storeFile`, `storePassword`, `keyAlias`, `keyPassword`. Without it, the release APK is unsigned.

### Tests

`testDebugUnitTest` runs everything on the JVM:

- **Unit tests** for the parser, redactor, AI JSON handling, storage and ViewModel.
- **End-to-end UI tests** (`app/src/test/.../e2e`) that drive the real Compose UI and ViewModel: onboarding, every tab, settings auto-save (including leaving the screen right away), search, swipe/done/restore, the details sheet, chat and error snackbars. `MainActivitySmokeTest` boots the real app cold.
- **Settings persistence tests** that check every setting survives an app restart.
- **Screenshot tests** that render each screen to `app/build/screenshots/`.

GitHub Actions (`.github/workflows/android.yml`) runs the tests on every push and uploads the APKs and screenshots as artifacts. To release, bump `versionName`/`versionCode`, move the changes under that version in [CHANGELOG.md](CHANGELOG.md), and push to `main`: CI publishes a GitHub release with the APK and that changelog section once tests pass. (Pushing a `vX.Y.Z` tag does the same.) To sign release builds in CI, add the `JARVIS_KEYSTORE_B64`, `JARVIS_STORE_PASSWORD`, `JARVIS_KEY_ALIAS` and `JARVIS_KEY_PASSWORD` repository secrets.
