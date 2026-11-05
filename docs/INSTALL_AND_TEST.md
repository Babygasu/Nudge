# Installing and testing Nudge on your phone

No USB cable, no Android Studio. Every push to GitHub builds a new APK automatically.

## 1. Get the latest APK

**On your phone** (easiest):

1. Open **https://github.com/babygasu/nudge/releases** in your phone's browser.
2. The newest build is at the top: **"Nudge debug build N (branch)"**.
   - Builds from `main` are normal releases; builds from other branches are marked *Pre-release*.
3. Under **Assets**, tap **`nudge-debug-N.apk`** to download it.

**On the laptop** (alternative): GitHub → **Actions** → the latest green run → **Artifacts** → `nudge-debug-N`. This downloads a `.zip` containing the APK, so you'd have to move it to the phone yourself. The Releases page is simpler.

> A build takes about 5–8 minutes after a push. If the newest release isn't there yet, check **Actions** to see whether it's still running or failed.

## 2. Install it

1. Tap the downloaded file (or open it from **Downloads**).
2. The first time, Android asks to allow your browser (e.g. Chrome) to **install unknown apps**. Allow it, go back, and tap **Install**.
3. Google Play Protect may warn that the app is unrecognised. This is expected for test builds: tap **More details → Install anyway**.
4. The app appears as **"Nudge (debug)"**.

**Updating:** install the newer APK over the old one. Your reminders and permissions are kept, because every build is signed with the same project debug key.
If Android ever says *"App not installed, package conflicts"*, the signature changed (for example, a build made without the shared key). Uninstall Nudge (debug) and install again; this erases its reminders.

## 3. First-run setup (about 2 minutes)

Onboarding walks you through each permission. All of them are optional:

| Step | Why | What breaks without it |
|---|---|---|
| Notifications | So reminders can alert you | Reminders only show inside the app |
| Notification access | So Nudge can read message notifications | No automatic detection; adding and sharing reminders still work |
| Choose apps | Nudge ignores every app you don't tick | — |
| Sender names | Store "Ama" instead of "them" | Titles are less specific |
| On-time reminders | Exact alarms | Reminders may arrive a few minutes late |
| Battery | Stops your phone pausing Nudge | Detection may stop after a while on Samsung, Xiaomi and similar phones |

**"Restricted setting" / switch greyed out:** because Nudge isn't installed from the Play Store, Android blocks notification access at first. Fix:
**Settings → Apps → Nudge (debug) → ⋮ (top-right) → Allow restricted settings → confirm**, then turn on notification access again. On some phones the ⋮ option only appears after you've tried to turn access on once.

## 4. Test checklist

Use this for each build and note anything that fails (screenshot + what you did).

### A. Reminders you add yourself
- [ ] Tap **Add**, create "Test reminder", set a time **2 minutes from now**, priority High. Save.
- [ ] It appears on **Today** under "Today".
- [ ] After 2 minutes a notification arrives with **Done** and **Snooze** buttons.
- [ ] Tap **Snooze** → the notification disappears and comes back after the snooze length (Settings → Snooze length; set it to 15 min to test faster).
- [ ] Tap **Done** from the notification → it moves out of Today. In **Reminders → Done & dismissed**, it's crossed out.
- [ ] Edit a reminder, change the time, and check that the new time is honoured.

### B. Detection using the built-in test messages
(Needs notifications and notification access on.)
- [ ] Settings → Test detection → tap **"Please send me the report before Friday."**
  - A test notification appears, then a quiet "Nudge noticed something" notification.
  - Today screen: "Send … the report" (Medium priority, due the day before Friday).
- [ ] Tap **"I was expecting the report yesterday…"**
  - The existing report reminder is **merged and escalated** (no duplicate), or a Critical "… is waiting for the report" if the first one was completed.
- [ ] Tap **"Call me when you are free. It's important."** → "Call … back", High.
- [ ] Tap **"Can we meet tomorrow at 10?"** → a meeting tomorrow at 10:00.
- [ ] Tap **"lol did you see the match last night?"** → **nothing** is created.
- [ ] Open a detected reminder → **Why Nudge noticed this** lists reasons, and there is no message text anywhere.
- [ ] Tap **Not a reminder** on a "Nudge noticed" notification → the item is dismissed.

### C. Real messages
- [ ] Ask a friend to send you, on WhatsApp: *"Can you send me the photos by tomorrow evening?"* → a reminder appears.
- [ ] A missed call → "Call … back" (if you enabled your Phone app).
- [ ] Long-press a message in any app → **Share → Nudge** → it suggests reminders; save one.
- [ ] Turn **Pause all detection** on (Privacy) → new messages create nothing. Turn it off again.
- [ ] Switch one app off in Privacy → its messages are ignored.

### D. Reliability
- [ ] Create a reminder for 5 minutes from now, then **swipe Nudge away from recent apps** → it still fires.
- [ ] Create a reminder for 5 minutes from now, then **restart the phone** → it still fires (allow a minute after boot).
- [ ] Turn on **airplane mode** and repeat A and B → everything works (Nudge has no internet access anyway).
- [ ] Leave the phone idle for an hour with a reminder due → it fires (maybe a little late if on-time reminders aren't allowed).

### E. Privacy
- [ ] Privacy Centre shows notification access status, per-app switches and what is stored.
- [ ] **Delete completed and dismissed** removes only finished items.
- [ ] **Delete all Nudge data** → the app returns to onboarding with no reminders. Messages in WhatsApp etc. are untouched.
- [ ] Phone Settings → Apps → Nudge (debug) → Permissions: **no internet/network permission is listed**.

### F. Floating bubble (optional)
- [ ] Settings → Show the Nudge bubble → grant "Display over other apps" → turn it on.
- [ ] The bubble shows the number of overdue + today items; drag it around; tap it to open Nudge.
- [ ] Drag it to the bottom edge to hide it.

## 5. Reporting problems

Open an issue on GitHub (or tell me) with:
- build number (Settings → About, e.g. `0.1.7-debug`),
- phone model and Android version,
- what you did, what you expected, what happened,
- screenshots, but **not** screenshots of private messages.

## Later: USB or wireless debugging

Not needed for Phase 1. When we want live logs or automated on-device tests, we'll set up
**Wireless debugging** (Android 11+, Developer options) or a USB cable, using the free Android
platform-tools (`adb`) without the full Android Studio.
