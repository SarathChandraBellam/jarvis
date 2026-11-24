# Changelog

All notable changes to J.A.R.V.I.S. HUD, newest first. Versions match `versionName` in
`app/build.gradle.kts`. Add each change under **Unreleased**. To release, rename that heading to the
version and date, bump `versionName` and `versionCode`, and push to `main`: CI publishes the GitHub
release with the APK and that section as the notes.

## [Unreleased]

## [2.0.0] - 2026-11-25

### Added
- **Warm calm personal wellness & subscriptions UI.** Redesigned across Today, Subs, Inbox, Ask, Onboarding, and Settings with paper-cream surfaces, pastel status accents, serif typography, and a floating pill navigation dock with detached Ask orb.
- **Today multi-view hub (`Overview · Food · Cycle · Sleep · Plan`).**
  - *Overview*: swipeable Daily Briefing with voice playback, Sleep / Movement / Renewals score rings, quick-log tiles, calorie & macro summary, and urgent items.
  - *Food*: local-first Indian & everyday dish macro estimator (plus optional OpenRouter AI estimation), calorie ring, triple macro bars, and meal logging.
  - *Cycle & period tracker*: 4-phase tracking (Menstrual, Follicular, Mid-cycle, Luteal), interactive monthly period calendar, predictions, phase guidance cards, proactive period nudges, and **Female / Male** profile toggle in Onboarding & Settings to show or hide cycle tracking.
  - *Sleep, Steps & Focus*: 7-day mini sleep rings and stage breakdown, Fasting & Deep Focus timer, hardware step counter (`Sensor.TYPE_STEP_COUNTER`, works without restricted Usage Stats), and workout logging.
  - *Plan*: daily habits & supplements checklist with weekly consistency bar, Today's calendar & reminders, recent calls, and device status.
- **Subscriptions & Bills hub (`Subs`).** Focused strictly on recurring subscriptions and open bills with monthly/yearly burn, category breakdown donut, free-trial alerts, pause/remove/add controls, and bill resolution (removed SMS daily spend/budget clutter).
- **Proactive notifications.** Configurable Android notifications and in-app nudges for upcoming subscription renewals, bills, deliveries, missed calls, planner reminders, and cycle reminders.
- **Version-safe persistence.** Preserves selected tabs, sub-tabs, inbox filters, profile/goals, habits, meals, sleep, workouts, steps, timers, and cycle history across app restarts and upgrades.

## [1.2.0] - 2026-11-23

### Fixed
- **Payments counted two or three times.** A payment announced by bank SMS, the bank app and a UPI app (GPay, PhonePe, Paytm) was stored once per message and added to spending each time. Reports of the same payment are now merged: matching UPI/bank reference, or the same amount and direction from different channels within 10 minutes that don't name different accounts. Real repeat payments through the same channel (two ₹50 chai payments) are kept. Duplicates saved by earlier versions are cleaned up on the next launch.
- "EMI … will be debited on …" was counted as money already spent; it is now an upcoming bill.
- "Card … has been used for INR …" was not recognised as a spend.
- "SBI Card … Total Amt Due" is now a credit card bill.
- The "new items" count after a refresh counts items actually added after merging.

### Changed
- **Money tab rebuilt as Overview · Spending · Bills.**
  - *Overview*: the month's spend and how it compares with the same point last month, the monthly budget, a cumulative this-month vs last-month line chart (tap or drag to read any day), per-day average, month-end pace, money in, bills due this week and recent payments.
  - *Spending*: one bar showing where the money went with a tappable legend, a month calendar shaded by daily spend (tap a day), and payments grouped by day that follow both filters.
  - *Bills*: open bills grouped into this week and later, named by biller (SBI Card, Airtel, BESCOM) with *Mark paid*, plus payments that repeat every month and their monthly total.
- The details sheet shows the sender, account and reference that identify a payment.
- Chart colours are checked for colour-blind separation and contrast; a category keeps its colour regardless of rank.

### Added
- CI publishes a GitHub release (APK + these notes) when a new version reaches `main`, or when a `v*` tag is pushed.

## [1.1.0] - 2026-11-23

**New signing key:** cannot update v1.0.x in place; uninstall first.

### Added
- Welcome screen on first launch (name and data access); upgrades from 1.0 skip it.
- Inbox search; pull to refresh on Home, Money and Inbox.
- Details sheet for any item: mark done, dismiss, restore, share.
- Monthly budget (Settings → Money) with progress on the Money tab.
- Clear chat on Ask.
- Tablet and landscape layout with a side navigation rail.
- End-to-end UI, settings persistence and screenshot tests; GitHub Actions CI.

### Changed
- Revamped UI with renamed tabs (Home · Money · Inbox · Ask). Demo mode removed.
- Granting a permission triggers a rescan.

### Fixed
- Settings not saving.

## [1.0.1] - 2026-11-23

### Changed
- Removed all UI animations; the app only redraws on real state changes, reducing CPU, GPU and battery use.

## [1.0.0] - 2026-11-22

- First public release: HUD, Finance, Intel and Ask tabs, daily briefing, OpenRouter or local parsing, redaction, encrypted API key and demo mode.
