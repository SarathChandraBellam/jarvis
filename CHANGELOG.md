# Changelog

All notable changes to J.A.R.V.I.S. HUD. Newest first.
Format: [Keep a Changelog](https://keepachangelog.com/en/1.1.0/); versions follow `versionName` in `app/build.gradle.kts`.

## [Unreleased]

### Fixed
- **Payments were counted twice (or three times).** A payment announced by bank SMS, the bank app and a UPI app (GPay, PhonePe, Paytm) was stored once per message and added to today's spend, the Finance tab and the briefing each time. Reports of the same payment are now merged: matching UPI/bank reference, or same amount and direction from different channels within 10 minutes that don't name different accounts. Real repeat payments through the same channel are kept. Duplicates saved by earlier versions are cleaned up on the next launch.
- "EMI … will be debited on …" was counted as money already spent; it is now an upcoming bill.
- "Card … has been used for INR …" was not recognised as a spend.
- "SBI Card … Total Amt Due" is now labelled a credit card bill.
- The sync status line counts items actually added after merging.

### Changed
- **Finance tab rebuilt as three views:**
  - *Overview*: month-to-date spend vs the same point last month, a cumulative this-month vs last-month line chart (tap or drag to read any day), per-day average, month-end pace, money in, bills due this week and recent payments.
  - *Spending*: one segmented bar by category with a tappable legend, a month calendar shaded by daily spend (tap a day), and payments grouped by day that follow both filters.
  - *Bills*: open bills grouped into this week / later with Paid and Hide, named by biller (SBI Card, Airtel, BESCOM), plus payments that repeat every month.
- Tapping a payment shows the original message it was read from, its sender, account and reference, and lets you hide it if it was misread.
- Finance uses its own quieter visual style: one sans face, sentence-case headings, and a category palette and calendar ramp checked for colour-blind separation and contrast on the app background. A category keeps its colour regardless of rank.
- HUD Screen Time panel shows today's total.
- Demo data includes echoed payments and six weeks of history.

### Added
- Finance items carry the sender, account last 4 digits and transaction reference (filled locally for AI results too).
- `TransactionDeduper` and `SpendInsights` (categories, merchants, recurring payments, income split, daily series), both covered by unit tests, including end-to-end tests that run the demo data through the real parser and store.

## [1.0.1] - 2026-11-23

### Changed
- Removed all UI animations; the app only redraws on real state changes, reducing CPU, GPU and battery use.

## [1.0.0] - 2026-11-22

- Initial public release: on-device HUD with HUD, Finance, Intel and Ask tabs, OpenRouter or local parsing, redaction, encrypted API key and demo mode.
