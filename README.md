# Nudge

**We remember what matters. Not everything you say.**

Nudge is a privacy-first Android assistant. It notices requests, promises, deadlines and
call-backs in your message notifications and turns them into calm, prioritised, explainable
reminders. It works entirely on your phone.

> Status: **Phase 1 (functional foundation)**: debug builds for testing on a real phone.

## Install a test build
Open **[Releases](https://github.com/Babygasu/Nudge/releases)** on your Android phone and download the
newest `nudge-core-dev-N.apk` (development builds, for testing only). The full build with message detection
(`nudge-full-dev-N.apk`) is installed via ADB; see [docs/PLAY_PROTECT.md](docs/PLAY_PROTECT.md). Full guide and test checklist: **[docs/INSTALL_AND_TEST.md](docs/INSTALL_AND_TEST.md)**.

## What works in Phase 1
- Onboarding that explains every permission; every permission can be skipped
- Today dashboard (overdue / today / this week / no date) and All Reminders with search and filters
- Add, edit, complete, snooze, dismiss and delete reminders
- On-time delivery with Done/Snooze buttons on the notification; survives app close and phone restart
- Notification capture from the apps you choose, including missed calls
- On-device detection of requests, commitments, follow-ups, meetings, call-backs, blocking and escalation, with priority and "why Nudge noticed this"
- Duplicate merging (a follow-up chase escalates the existing reminder)
- Share → Nudge from any app, or paste a message
- Privacy Centre: per-app switches, pause all, sender-name control, data summary, delete all data
- Optional floating bubble
- No internet permission, encrypted database, no backups, no message text stored

## Project layout
```
app/                    Android app (Compose UI, capture, data, scheduling, security, bubble)
core/intelligence/      Pure-Kotlin detection engine + unit tests
docs/                   Plan, decisions, limitations, install & test guide
.github/workflows/      CI: test → build APK → publish to Releases
```

## Docs
- [Phase 0 plan](docs/PHASE0_PLAN.md): architecture, privacy model, integrations, roadmap
- [Decisions](docs/DECISIONS.md)
- [Known limitations](docs/KNOWN_LIMITATIONS.md)

## Building locally (optional)
Requires JDK 17 and the Android SDK (or Android Studio). Then run `gradle assembleDebug` with Gradle 8.10+.
CI is the reference build; local tooling isn't needed to get an APK.
