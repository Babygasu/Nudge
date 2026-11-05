# Nudge — Phase 0: Repository Assessment & Technical Plan

Status: **Draft for product-owner approval** · Date: 2026-11-05
Nothing in this document has been implemented yet. Statements about Android and third-party
capabilities were checked against public documentation/reporting in November 2026; items marked
**(verify on device)** must be confirmed on a real phone before we rely on them.

---

## 1. Product understanding

Nudge is a calm, privacy-first assistant that notices **obligations** hidden in everyday digital
traffic — requests, promises, deadlines, meetings, follow-ups, missed calls that need a call back —
and turns them into a small number of well-prioritised, explainable reminders.

What makes it different from a reminder app:

| Principle | What it means in practice |
|---|---|
| Understands meaning, not keywords | "We cannot proceed until you submit the document" outranks "urgent!!" in a group joke. |
| Explains itself | Every reminder carries a *why*: deadline, sender importance, blocking language, escalation. |
| Offline-first | Detection, storage, scheduling and delivery work in airplane mode, after a reboot, with the app closed. Cloud AI is an optional refinement. |
| Remembers obligations, not conversations | Message text is processed in memory and discarded. Only the structured reminder is kept. |
| Fits the user's ecosystem | Reminders can live in Nudge, the phone calendar, Google Tasks or Microsoft To Do (→ Samsung Reminder). |
| User in control | Per-app monitoring, pause-all, cloud off-switch, delete-everything, editable personal context. |

Two memories, never mixed:
- **Reminder memory** — what the user has to do. Lives until completed/deleted.
- **Personal context** — what matters to the user (projects, key people, responsibilities). Optional, editable, deletable, only from what the user tells Nudge.

## 2. Repository assessment

| Item | Finding |
|---|---|
| Repo | `babygasu/nudge`, **public**, created 2026-11-05, default branch `main` |
| Contents | One commit (`0c7b9ab Initial commit`), one file: `README.md` containing `# Nudge` |
| Android project | **None.** No Gradle files, no source, no dependencies, no CI |
| Local machine | Repo now cloned into `Desktop/Nudge`. **No JDK, no Android SDK, no adb, no Gradle installed** |

Conclusion: green-field. Nothing to preserve beyond the README. Two things to note:
1. **The repo is public.** That is fine for an open-source privacy product (it lets people verify our claims), but it means signing keys, OAuth client secrets and API keys must *never* be committed. Please confirm public is intended.
2. We cannot build an APK on this PC yet — see §10 for the proposed build route.

Proposed repository layout:

```
nudge/
├─ app/                      # Android application: Compose UI, navigation, DI wiring
├─ core/
│  ├─ model/                 # Pure Kotlin domain types (Reminder, Priority, Source, …)
│  ├─ data/                  # Room DB (encrypted), DAOs, repositories, DataStore settings
│  ├─ security/              # Keystore, DB key management, redaction, safe logging
│  └─ common/                # Clock, dispatchers, result types
├─ feature/
│  ├─ capture/               # NotificationListenerService, per-app filters, share-to-Nudge
│  ├─ intelligence/          # Local engine: segment → detect → extract → score → explain → dedupe
│  ├─ reminders/             # Reminder engine, AlarmManager/WorkManager scheduling, boot recovery
│  ├─ context/               # Personal Context Profile
│  ├─ integrations/          # Adapter API + one sub-package per provider
│  ├─ cloud/                 # Optional cloud interpreter (disabled by default)
│  └─ bubble/                # Optional floating bubble
├─ testdata/                 # Synthetic message corpus for intelligence tests (never real messages)
├─ docs/                     # This plan, ADRs (decision records), threat model, limitations
└─ .github/workflows/        # CI: build, test, produce APK
```

## 3. Proposed technical architecture

**Stack:** Kotlin · Jetpack Compose (Material 3) · MVVM with unidirectional data flow (ViewModel + `StateFlow`) · Hilt (dependency injection) · Room + SQLCipher · DataStore · WorkManager + AlarmManager · Android Keystore · Kotlin Coroutines · minSdk 26 (Android 8.0), targetSdk = latest stable.

Why native Kotlin rather than Flutter/React Native: every hard part of Nudge — notification listening, exact alarms, boot recovery, overlays, Keystore, on-device ML — is Android-platform code. A cross-platform layer would add a bridge without removing that work. iOS cannot run a notification listener at all, so cross-platform buys little.

```
 Notifications ─┐   Share sheet / quick add ─┐
                ▼                            ▼
        ┌─────────────── capture ───────────────┐   in-memory only
        │ per-app allow-list · pause · de-noise │
        └──────────────────┬────────────────────┘
                           ▼
        ┌──────────── intelligence ─────────────┐
        │ LocalEngine (always) ──► result        │
        │ OnDeviceLLM (if available, foreground) │
        │ CloudEngine (opt-in, online)           │
        └──────────────────┬────────────────────┘
                           ▼  structured Candidate (no raw text)
        ┌──────────── reminder engine ──────────┐
        │ dedupe/merge · priority · explanation  │
        │ save ─► verify ─► discard raw text     │
        └───────┬───────────────┬───────────────┘
                ▼               ▼
      Room (encrypted)   Scheduler (AlarmManager + WorkManager)
                ▼               ▼
      UI (Compose)       Notifications · Bubble · Integration outbox
```

Key interfaces (so features can be swapped without rewrites):
- `IntelligenceEngine.analyze(CapturedItem): List<Candidate>` — local, on-device-LLM and cloud engines all implement it.
- `IntegrationAdapter` with declared `capabilities: Set<READ, WRITE, OBSERVE, SYNC>`.
- `ReminderScheduler` hides AlarmManager/WorkManager choices.

## 4. Offline-first and hybrid intelligence

**Layer 1 — Local engine (always on, works offline, Phase 2).** Not a keyword list; a scored linguistic pipeline:
1. *Normalise & segment* — split notification text into sentences; handle grouped/multi-message notifications.
2. *Speech-act detection* — weighted patterns for requests ("can you…", imperatives, questions addressed to the user), commitments ("I'll…", "will do", "let me get back to you"), dependencies/blocking ("cannot proceed until…"), escalation ("I was expecting… yesterday", "still waiting"), meetings, call-back requests.
3. *Entity & time extraction* — our own `java.time`-based parser for relative expressions ("by Friday", "end of day", "in 2 hours", "next week") plus **ML Kit Entity Extraction** (on-device, free) for dates/times, phone numbers, addresses. Its model is downloaded once and then works offline.
4. *Scoring* — actionability score; priority from **urgency** (time to deadline), **importance** (key contact, project match from personal context), **consequence** (blocking language), **escalation** (follow-ups), and **learned user weights**.
5. *Explanation* — built from the signals that fired, e.g. "Due Friday (2 days) · from a key contact · they say work is blocked".
6. *Dedupe/merge* — compare against open reminders (normalised action + object + sender key + time window). A follow-up like "I was expecting the report yesterday" **merges into and escalates** the existing "Send report" reminder rather than creating a second one.

Phase 2 can add a small **on-device text classifier** (LiteRT/TensorFlow Lite, trained by us on a synthetic + consented corpus) to improve recall beyond patterns.

**Layer 2 — On-device LLM (optional, where hardware supports it).** Google's ML Kit GenAI Prompt API runs Gemini Nano locally on supported phones (recent Pixels, Galaxy S26, some Xiaomi/vivo/Honor). Constraints: limited device list, Alpha status, and **inference only while the app is in the foreground** — so it can refine reminders when the user opens Nudge, not when a notification arrives in the background.

**Layer 3 — Cloud AI (optional, opt-in, Phase 3).** Used only when the user has consented and the device is online at the moment of processing. Sends a redacted minimum (the relevant sentence(s), names/numbers masked, no sender identity). If the call fails or times out, the Layer 1 result stands — the reminder is never lost.

What offline **cannot** do (stated honestly in the app): Nudge can only process what has already reached the phone. If WhatsApp or Gmail haven't delivered a message because the phone is offline, Nudge cannot see it.

## 5. Ephemeral processing and minimal retention

Lifecycle implemented as one in-memory transaction:

1. `onNotificationPosted` → copy only needed fields (package, title, text lines, timestamp, conversation flag) into an in-memory `CapturedItem`. Never written to disk, never logged.
2. Run the intelligence engine (milliseconds for Layer 1).
3. Build a `Candidate` with action, due time, priority, explanation codes — **no raw text**.
4. Insert/merge in Room inside a transaction; read back to verify.
5. Drop the `CapturedItem` reference and overwrite its text buffers.

Supporting controls:
- **Safe logging:** a `NudgeLog` wrapper with no API for free text from messages; release builds strip debug logs; lint rule forbidding `Log.*` on capture types.
- **No analytics or crash-reporting SDKs.** Crashes stay local; a user can choose to export a sanitised report.
- **Backups:** `android:allowBackup="false"` plus `dataExtractionRules` excluding everything, so the database never goes to Google Drive or device-to-device transfer.
- **Encryption at rest:** Room via SQLCipher; the database key is generated randomly and wrapped by a non-exportable Android Keystore key. OAuth tokens stored encrypted the same way, never in plain prefs.
- **Sender identity:** stored only if the user enables it. Otherwise we keep a keyed hash (HMAC with a Keystore key) so dedupe and "this person matters" learning still work — pseudonymous, not readable.
- **Source messages are never touched.** Nudge has no permission to delete/modify WhatsApp/Gmail content and will never request one; "discard" means only our own memory.

Trade-off you should know about: strict in-memory processing means that if Android kills Nudge *during* the few milliseconds of processing, that one notification is missed. Also, cloud refinement can only happen if online at that instant (we don't queue raw text). See decision D4.

## 6. How Android limits what Nudge can see

| Source | Legitimate route | What Nudge actually gets | Limits |
|---|---|---|---|
| WhatsApp, Telegram, Signal, Slack, Teams… | `NotificationListenerService` (user grants "Notification access") | Sender name, message text as shown in the notification, group name | Nothing if the chat is muted, notifications are off, or the chat is open on screen. Media shows as "📷 Photo". Long messages may be truncated. No history, no outgoing messages. |
| Gmail / Outlook email | Notification listener | Sender, subject, snippet | Snippet only. Full Gmail API is a *restricted* scope requiring a paid third-party security assessment — not recommended. |
| SMS | Notification listener on the SMS app | Sender + text as notified | `READ_SMS` is restricted to the default SMS app under Play policy — we will not request it. |
| Missed calls | Notification listener on the phone/dialer app's "Missed call" notification | Caller name/number as shown | `READ_CALL_LOG` is Play-restricted — not requested. Wording differs per OEM **(verify on device)**. |
| User's own promises ("I'll send it tomorrow") | Share-to-Nudge, quick add, voice entry; sometimes the notification shows your inline reply | Whatever the user shares | **Important gap:** notifications almost never contain messages the user sends. Most of the user's own commitments are invisible unless shared. |
| Calendar events | `CalendarContract` provider (READ/WRITE_CALENDAR) | Events from every account synced to the phone | Works offline. Normal runtime permission. |

Platform rules affecting design:
- **Sideloaded APK + "Restricted settings"** (Android 13+): Android blocks notification access for apps not installed from an app store until the user opens *App info → ⋮ → Allow restricted settings*. Onboarding must walk the user through this; menu placement varies by manufacturer **(verify on device)**.
- **Android 15+ OTP redaction:** one-time codes are hidden from third-party listeners. Irrelevant to Nudge (and desirable).
- **Exact alarms:** Android 14+ does not grant `SCHEDULE_EXACT_ALARM` by default. We ask for it with an explanation; if refused, reminders still fire within a short window (inexact) and the app says so.
- **Battery/OEM killing:** Samsung, Xiaomi etc. aggressively stop background apps. Scheduled reminders survive (they're system alarms), but the listener can be unbound. We detect this, call `requestRebind`, and guide the user to exclude Nudge from battery optimisation.
- **Developer verification for sideloading:** Google began enforcing developer verification on 30 Sept 2026 in Brazil, Indonesia, Singapore and Thailand, with a global rollout planned for 2027. Installing via **ADB (USB cable) remains exempt**. For long-term distribution we will need to register in the Android Developer Console (decision D8).
- **No accessibility-service tricks.** We will not use AccessibilityService to read screens — Play policy prohibits it for this purpose and it would betray the privacy promise.

Every permission is optional except notifications for delivering reminders; manual reminders, dashboard, privacy centre and scheduling all work with nothing else granted.

## 7. Realistic integrations

| Service | Read | Write | Observe | Sync | Route & notes |
|---|---|---|---|---|---|
| **Phone calendar** (Google Calendar, Samsung Calendar, Outlook-synced calendars) | ✅ | ✅ | — | ✅ by OS | `CalendarContract`. Offline, free, no Google review. **Recommended instead of the Google Calendar REST API** for v1. |
| **Google Tasks** | ✅ | ✅ | — | ✅ (polling) | Tasks REST API + Google sign-in. Scope is *sensitive*: fine for ≤100 test users; public release needs Google verification (free, days–weeks). Online only → writes go through an outbox queue. |
| **Microsoft To Do / Outlook** | ✅ | ✅ | ✅ (notifs) | ✅ (delta queries) | Microsoft Graph `todo` API via MSAL; free Entra app registration. |
| **Samsung Reminder** | ❌ | ⚠️ indirect | ✅ (notifs) | ⚠️ | **No public API.** Samsung Reminder has a built-in Microsoft To Do sync, so Nudge → To Do → Samsung Reminder is the supported bridge **(verify on device)**. We will not use reverse-engineered private APIs. |
| Gmail | ❌ | — | ✅ (notifs) | — | Gmail API = restricted scope + paid security assessment; not planned. |
| WhatsApp | ❌ | — | ✅ (notifs) | — | No API for personal accounts. |

Rules: every adapter falls back to a local reminder when unavailable; we check existing events/tasks before writing (dedupe); we never edit or delete external items without explicit user action; each external copy is linked (`external_links`) so completion can sync both ways.

## 8. Database structure (what is retained)

All tables live in the encrypted Room database. **No table has a column for message bodies.**

| Table | Key fields | Notes |
|---|---|---|
| `reminders` | id, title (action), notes (user-written only), kind (task/commitment/meeting/callback/follow-up/deadline), due_at, due_precision (exact/day/approximate/none), priority (CRITICAL/HIGH/MEDIUM/LOW), priority_source (auto/user), status (open/done/dismissed/snoozed), snoozed_until, created_at, updated_at, completed_at, source_app (nullable), sender_display (nullable, only if enabled), sender_key (HMAC), explanation_codes (list), confidence, dedupe_key, recurrence_rule (nullable) | The core "reminder memory". |
| `reminder_events` | id, reminder_id, type (created/merged/escalated/snoozed/completed/edited/corrected), at, detail_code | Audit trail without content; powers "why" history and learning. |
| `monitored_apps` | package, label, enabled, added_at | Privacy Centre per-app switches. |
| `context_items` | id, type (project/responsibility/goal/recurring/priority-rule), label, keywords, weight, created_at | Personal Context Profile; entirely user-entered. |
| `important_contacts` | id, display_name, sender_key, weight | User-entered; optional. |
| `feedback_weights` | feature, scope (global/app/sender_key), weight, updated_at | Learned from corrections (dismiss = less, raise priority = more). Numbers only. |
| `integration_accounts` | id, provider, account_label, capabilities granted, status, connected_at | Tokens are *not* here — they're Keystore-encrypted in a separate store. |
| `external_links` | reminder_id, provider, external_id, etag, last_synced_at | Two-way sync + dedupe with external apps. |
| `outbox` | id, provider, operation, payload (structured reminder only), attempts, next_attempt_at | Offline queue for integration writes. |

Settings (notification behaviour, quiet hours, thresholds, cloud consent, bubble) live in DataStore.
"Delete all Nudge data" wipes the database file, the DataStore, tokens and the Keystore keys, and cancels all alarms/work.

## 9. Phased development plan

Each milestone ends with a green CI build, passing tests and an installable APK.

**Phase 1 — Functional foundation**
- 1.1 Project skeleton, modules, CI that builds a debug APK, safe-logging, backup exclusion.
- 1.2 Encrypted Room DB, reminder model, manual create/edit/complete/snooze/delete.
- 1.3 Scheduling: AlarmManager + reminder notifications with Done/Snooze actions (work without opening the app), boot/time-zone/app-update recovery, WorkManager reconciler that marks overdue items.
- 1.4 UI: onboarding with honest permission explanations, Home dashboard (today / upcoming / overdue / important), All Reminders with search & filter, Settings.
- 1.5 Notification capture: listener service, per-app allow-list, pause-all, "restricted settings" guidance, a *basic* detector so the pipeline works end-to-end.
- 1.6 Privacy Centre v1 (permissions, monitored apps, retained data view, delete one/all) and basic floating bubble.

**Phase 2 — Intelligence engine**: full local pipeline (§4), time parser + ML Kit entities, prioritisation with explanations, dedupe/merge/escalation, corrections → learned weights, synthetic test corpus with measured precision/recall, optional Gemini Nano refinement.

**Phase 3 — Personalisation & integrations**: Personal Context Profile, phone-calendar adapter, Google Tasks, Microsoft To Do (→ Samsung Reminder bridge), outbox + conflict handling, optional cloud AI with consent screen and disclosure.

**Phase 4 — Reliability, privacy & release**: threat model review, offline/restart/Doze/battery testing on physical devices (incl. Samsung), permission-denial matrix, integration failure tests, security review, signed release APK, known-limitations document.

## 10. Build and APK testing strategy

> **Update (Phase 1):** Android Studio is *not* a prerequisite. APKs are built by GitHub Actions and
> installed over the air from GitHub Releases. See [INSTALL_AND_TEST.md](INSTALL_AND_TEST.md) and D-003 in
> [DECISIONS.md](DECISIONS.md). USB/wireless debugging will be added later when needed.

**Building** (nothing is installed on this PC today):
- **GitHub Actions (recommended, free for a public repo):** every push builds, runs unit tests and attaches a downloadable debug APK. This is the source of truth and lets you get an APK without any local tooling.
- **Android Studio on this PC (recommended alongside):** needed for USB install via `adb`, on-device debugging and running instrumented tests. ~10 GB disk. I'll give step-by-step instructions when we start Phase 1.

**Installing on your phone:** enable Developer options → USB debugging, then `adb install` (exempt from the new sideloading verification), or download the APK from GitHub and install it (needs "install unknown apps", and then "Allow restricted settings" for notification access).

**Testing layers:**
- JVM unit tests: intelligence engine against a synthetic corpus (hundreds of realistic, invented messages with expected outcomes), priority rules, dedupe, time parsing with a fixed clock.
- Robolectric/instrumented tests: Room migrations, encryption, scheduling, boot receiver, notification actions, data deletion.
- Fake notification generator (debug build only): posts realistic WhatsApp/Gmail-style notifications so the full pipeline can be tested on a device without real messages.
- Manual device checklist each milestone: airplane mode, reboot, force-stop, Doze (`adb shell dumpsys deviceidle force-idle`), permission denied/revoked, battery-optimised mode, time-zone change.
- Signing: debug key for now; the release key is created later and kept **out of the repo** (GitHub encrypted secrets).

## 11. Major risks and mitigations

| # | Risk | Impact | Mitigation |
|---|---|---|---|
| R1 | User's own outgoing commitments are mostly invisible to notification listening | Core use case partially unmet | Share-to-Nudge, quick add, voice capture; capture inline replies when shown; be explicit in onboarding. |
| R2 | Notification content is partial (muted chats, truncation, media, open chat) | Missed items | Explain limits; "missed something? Add it in one tap"; never claim completeness. |
| R3 | OEM battery management unbinds the listener | Silent capture gaps | Rebind on connect/disconnect, health check in dashboard, guided battery-exclusion. |
| R4 | Rule-based detection has false positives/negatives; languages beyond English | Noise erodes trust | Conservative thresholds, explanations, one-tap dismiss that learns, measured corpus tests, decision D3 on languages. |
| R5 | Exact-alarm permission denied | Reminders a few minutes late | Inexact fallback + clear UI; don't over-promise precision. |
| R6 | Sideload friction ("restricted settings", developer verification 2027) | Install/onboarding confusion | Guided onboarding; register with Android Developer Console before 2027 / Play release. |
| R7 | Google Tasks sensitive-scope verification; Gemini Nano limited devices; ML Kit model needs one-time download | Integration/feature delays | Phone-calendar first; test-user mode for Tasks; on-device LLM strictly optional. |
| R8 | Cloud AI needs an API key that can't be safely embedded in an APK | Cost/security | Either user-supplied key (stored in Keystore) or a small proxy server (has hosting cost) — decision D6. |
| R9 | Floating overlay needs a foreground service; Android restricts these | Battery, policy, a persistent notification | Bubble off by default; shown only when there are high/critical items; decision D7. |
| R10 | Public repo leaks secrets | Account compromise | `.gitignore` for keys, secret scanning, secrets only in GitHub Actions secrets. |
| R11 | Encrypted DB adds native library size/complexity (SQLCipher) | Larger APK (~+5–7 MB) | Acceptable for privacy promise; isolated in `core:data`. |

## 12. Decisions needed from the product owner

| ID | Decision | My recommendation |
|---|---|---|
| D1 | Approve the architecture (native Kotlin/Compose, modules, encrypted Room) | Approve |
| D2 | Which phone(s) will we test on (make, model, Android version)? | Needed before Phase 1.5 |
| D3 | Languages Nudge must understand at launch | English first; design the engine for more |
| D4 | Ephemeral strictness: pure in-memory (may miss an item if killed mid-processing; no deferred cloud) vs a tiny encrypted queue that holds raw text ≤10 minutes | Pure in-memory |
| D5 | Use the phone's calendar provider instead of the Google Calendar API | Yes |
| D6 | Cloud AI: provider, and user-supplied key vs our own proxy (cost) | Defer to Phase 3; no cloud in Phases 1–2 |
| D7 | Bubble: overlay bubble (needs "display over other apps" + foreground service) vs a persistent quick-access notification/home-screen widget | Overlay bubble, off by default, plus a widget |
| D8 | Distribution target: personal sideload/testing only, or Google Play later | Design to Play policy from day one |
| D9 | Keep the GitHub repo public? | Yes, if you're comfortable — it supports the privacy story |

## Sources consulted

- Samsung Reminder API: [Samsung developer forum](https://forum.developer.samsung.com/t/samsung-reminder-api/14356), [Samsung community — To Do sync](https://r2.community.samsung.com/t5/Others/Better-Integration-of-Samsung-Reminders-with-Google-and/m-p/20750896)
- Exact alarms: [Android — Schedule alarms](https://developer.android.com/training/scheduling/alarms), [Android 14 exact-alarm change](https://developer.android.com/about/versions/14/changes/schedule-exact-alarms)
- On-device LLM: [ML Kit GenAI overview](https://developers.google.com/ml-kit/genai), [Prompt API announcement](https://developer.android.com/blog/posts/ml-kit-s-prompt-api-unlock-custom-on-device-gemini-nano-experiences)
- Google Tasks scopes: [Tasks API auth](https://developers.google.com/tasks/auth), [Sensitive scope verification](https://developers.google.com/identity/protocols/oauth2/production-readiness/sensitive-scope-verification)
- Sideloading: [Google — Android developer verification](https://support.google.com/android-developer-console/answer/16561738), [Android Authority — rollout](https://androidauthority.com/android-sideloading-developer-verification-first-wave-rollout-3717921)
- Restricted settings: [Android Authority — Android 15 restricted settings](https://androidauthority.com/android-15-restricted-settings-sideloading-3481098)
- OTP redaction: [Android Authority — Android 15 sensitive notifications](https://www.androidauthority.com/android-15-sensitive-notifications-3416414)
