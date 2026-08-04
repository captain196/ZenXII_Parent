# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

**This is the ZenXii Parent app — one of five surfaces of the ZenXii ERP.** Full cross-system
architecture, contracts and deploy rules live in `/Users/yuggi/AndroidStudioProjects/CLAUDE.md`. Read
it before changing anything that touches Firestore, push, or fees — most changes here need a matching
change in the admin panel (`~/Desktop/Zennxii_adminPanel`) or the Teacher app.

Parent is **read-mostly**: it consumes what the panel and staff app produce. Its few write paths —
homework submission, leave requests, PTM booking, fee payment — are the ones to treat carefully.

## Commands

```bash
./gradlew assembleDebug
./gradlew installDebug
./gradlew :app:testDebugUnitTest                                  # 3 JVM tests, green
./gradlew :app:testDebugUnitTest --tests "*HomeworkDateLogicTest"
./gradlew bundleRelease                                           # needs keystore.properties (gitignored)
```
Gradle 8.2 · JVM 17 · minSdk 24 · target/compileSdk 35 · Compose BOM 2024.02 · Hilt 2.50 + KSP.
`BASE_URL` = `https://www.zenxii.com/Grader/school/` — **different from the Teacher app**, which
points at the host root. Don't copy a Teacher endpoint path across without checking which base it
assumes.

## Layering

`data/firebase` (`FirestoreService`, `FirebaseAuthManager`) → `data/repository/firestore/*` (23 repos)
→ `ui/<module>/`. Hilt wiring in `di/AppModule.kt`.

- `data/remote/` — `ApiService`, `AuthApi`, `FeesApi`, `PtmApi`, plus `BaseUrlInterceptor`, which
  rewrites scheme/host/port of every request to a dev override set in the Dev Settings dialog. It
  no-ops when the override is empty or unparseable, so a "wrong server" bug is usually a stale
  override in `DevPrefs`, not the compiled `BASE_URL`.
- `util/Constants.kt` — **the** source of collection names (`object Firestore`). Never inline a
  collection string. `object Firebase` holds legacy RTDB paths; `data/model/rtdb/` is the remaining
  RTDB surface.
- `service/FCMService.kt` + `util/DeepLinkBridge.kt` — push receipt and deep links. The app never
  sends push; the Cloud Function dispatches from `pushRequests`.
- Payments: `ui/payment/` + `ui/fees/` + `util/ReceiptPdfGenerator.kt`. Razorpay needs its ProGuard
  keep rules in release builds.
- `data/model/firestore/` (70 files) mirrors server document shapes — a field rename is a
  cross-system contract change.

## Local rules

- Dialogs, sheets and forms must scroll and fit small screens and landscape (height cap,
  `verticalScroll`, sticky footer, `imePadding`).
- Login is a synthetic email `{userId.lowercase()}@schoolsync.app`; `schoolId` comes from ID-token
  claims, never from user input. A claims change needs a re-login to take effect.
- Every query is scoped by `school_id` **and** academic session — dropping the session filter is what
  makes parents see last year's data.
- New Firestore queries usually need a composite index, which lives in the panel repo
  (`firebase-rules/firestore.indexes.json`) and must be deployed **before** the app ships.
- Don't commit, push, or build a release without asking. Leave work UNCOMMITTED and say so.
