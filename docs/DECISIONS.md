# Decision log

Short records of significant technical decisions. Newest first.

## D-006 · Standard debug signing for development builds (2026-11-05)
The product owner chose zero-configuration testing over update continuity. CI now uses Android's auto-generated
debug key: no secrets, no keystore in GitHub. The cost, accepted: certificates can differ between builds, so an
update may need an uninstall that erases test data. Stable signing (secrets + certificate check) is deferred until
stable releases. The workflow uses a read-only token for building and a separate write-only publish job that never
runs for pull requests. Supersedes the shared-debug-key part of D-003.

## D-005 · Defaults for open product decisions (2026-11-05)
The product owner approved the Phase 0 plan without choosing D2–D9. Phase 1 uses the recommended defaults, all reversible:
English first · message text held in memory only (no queue) · overlay bubble, off by default · phone calendar instead of the Google Calendar API (Phase 3) · no cloud AI before Phase 3 · design to Google Play policy · public repository.

## D-004 · No internet permission in Phase 1
`android.permission.INTERNET` is explicitly removed in the manifest (`tools:node="remove"`), so the app *cannot* send data anywhere, even if a library tried. This makes the privacy promise verifiable in Android's own app-info screen. Will be revisited only when an opt-in integration or cloud AI needs it.

## D-003 · Build in GitHub Actions, install over the air
The product owner tests on a physical phone without USB or Android Studio. Every push runs unit tests, builds a debug APK, uploads it as an artifact, and publishes it as a GitHub Release so it can be downloaded directly on the phone. A shared debug signing key is stored as an encrypted repository secret so each build installs as an update. Gradle is provided by `gradle/actions/setup-gradle` (no Gradle wrapper committed yet, because there's no local Java toolchain to generate one; it can be added when Android Studio or a JDK is set up).

## D-002 · Two Gradle modules, manual dependency wiring
`:core:intelligence` is pure Kotlin (no Android), so detection logic is fast to test and can't access storage, network or logs by itself. Everything else lives in `:app`, organised by package (`capture`, `data`, `reminders`, `security`, `bubble`, `ui`). Dependencies are wired by hand in `AppContainer` instead of Hilt. That's simpler to build and enough for this size. Packages can become modules later without changing their code.

## D-001 · Native Kotlin + Jetpack Compose
All of Nudge's hard parts (notification listening, alarms, boot recovery, overlays, Keystore) are Android platform APIs, so a cross-platform framework would add a layer without removing work. See `PHASE0_PLAN.md` §3.

## Encryption at rest
Room with SQLCipher (`net.zetetic:sqlcipher-android`). The database passphrase is 32 random bytes, encrypted with an AES-GCM key held in the Android Keystore and stored in no-backup storage. Backups and device-to-device transfer exclude all app data.
