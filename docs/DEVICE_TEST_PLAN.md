# Phase 1: real-device test plan

**Gate:** Phase 1 is merged into `main` only when every **★ critical** test passes on your phone.
Other tests are important but can become Phase 2 fixes.

Record for each test: ✅ pass · ❌ fail (what happened + screenshot, never of private messages) · ⏭ skipped.
Note at the top: **phone model, Android version, build number** (Settings → About).

**Two rounds** (see PLAY_PROTECT.md for why):
- **Round 1: Nudge Core**, installed from the phone's browser: sections **2, 3, 4.4, 5.1–5.3, 6.1, 6.5, 6.6**.
  In Core, use Share → Nudge or the ✎ paste screen wherever a test mentions "Test detection".
- **Round 2: full Nudge**, installed over ADB: sections **1, 4.1–4.3, 5.4–5.6, 6.2, 6.3** (plus a quick repeat of 3.1).

**Before each round:** install the newest build (see INSTALL_AND_TEST.md) and complete onboarding: notifications on,
on-time reminders allowed, battery set to Unrestricted. For the full build, also turn on notification access
(incl. "Allow restricted settings"), tick WhatsApp + Phone, and leave sender names **on** (easier to read).

---

## 1. Notification detection
| # | Test | Expected |
|---|---|---|
| ★1.1 | Settings → Test detection → "Please send me the report before Friday." | "Nudge noticed" notification; reminder *Send Ama (test) the report* on Today/This week |
| ★1.2 | A friend sends you on WhatsApp: *"Can you send me the photos by tomorrow evening?"* (chat **closed**, not muted) | Reminder *Send <name> the photos*, due tomorrow ~18:00 |
| 1.3 | Same friend sends it while you have their chat **open** | Nothing (WhatsApp doesn't notify; known limitation) |
| ★1.4 | Let a call ring out (missed call) | *Call <name> back* reminder |
| 1.5 | Receive an email in Gmail asking you to do something | Reminder from Gmail, or nothing if only the subject is visible. Note which |
| ★1.6 | Privacy → switch WhatsApp **off**, friend sends a request | Nothing created. Switch back on |
| ★1.7 | Long-press a message → Share → Nudge | Suggestions appear; saving one creates the reminder |

## 2. Contextual understanding
Use Share → Nudge or the paste screen (✎ on Today) for these:
| # | Message | Expected |
|---|---|---|
| ★2.1 | "I was expecting the report yesterday. We cannot proceed without it." | Critical; reasons include *still waiting* + *can't move forward* |
| 2.2 | "URGENT lol did you see the game last night?" | Nothing found |
| 2.3 | "No rush, but could you send the invoice when you get a chance" | *Send the invoice*, Low |
| 2.4 | "We cannot proceed until you submit the document" | *Submit the document*, High |
| 2.5 | "I will submit the proposal by 5pm tomorrow" | *Submit the proposal*, tomorrow 17:00, your commitment |
| 2.6 | "Your OTP is 482913" | Nothing found |
| 2.7 | Three messages of your own choosing, the way people really write to you | Note anything wrong or missed. This is the most useful input for Phase 2 |
| ★2.8 | Open any detected reminder | *Why Nudge noticed this* shows reasons; **no original message text** anywhere |

## 3. Offline reminders
| # | Test | Expected |
|---|---|---|
| ★3.1 | Airplane mode **on**. Add a reminder for +3 min | Fires on time with Done/Snooze |
| ★3.2 | Still offline: Test detection sample 1.1 | Detected exactly as online |
| ★3.3 | From the notification tap **Snooze** (set Snooze length to 15 min first) | Disappears, returns ~15 min later |
| ★3.4 | Tap **Done** on a reminder notification | Removed from Today; shown crossed out under Reminders → Done |

## 4. Duplicate detection
| # | Test | Expected |
|---|---|---|
| ★4.1 | Test detection 1.1, then "I was expecting the report yesterday…" | **One** reminder, raised in priority, *Merged…* reason. No second report reminder |
| 4.2 | Tap the same test sample twice | Still one reminder |
| 4.3 | Two missed calls from the same person | One *Call back* reminder |
| 4.4 | Share "Please review the proposal" and "Please submit the proposal" | **Two** reminders (different actions) |
| ★4.5 | Share "Please submit the project report by Monday at 10 AM.", then "I'm still waiting for the project report. We cannot proceed until you submit it." | **One** reminder *Submit the project report*, priority raised, deadline still **Monday 10:00** |
| ★4.6 | Share "Please send the project report by Friday", then "Please submit the project report by Monday" | Second one is saved but marked **Possible duplicate**. Opening it shows *Merge / Keep both*. Merge leaves one reminder due Monday |
| 4.7 | Share "Please send the expense report" after 4.5 | A **separate** reminder (different report) |

## 5. Battery and background
| # | Test | Expected |
|---|---|---|
| ★5.1 | Reminder for +5 min, then swipe Nudge away from Recents | Fires on time |
| ★5.2 | Reminder for +5 min, then **restart the phone** (don't open Nudge) | Fires on time or within ~1 min of boot finishing |
| ★5.3 | Leave the phone locked and untouched for 2+ hours with a reminder due in the middle | Fires (note how late, if at all) |
| 5.4 | After 5.3, without opening Nudge, have a friend send a request | Detected. If not, open Settings → Reliability and note what it says |
| 5.5 | Settings → Battery: set back to *Optimised*, repeat 5.3 overnight | Note any delay. Tells us how much the battery setting matters on your phone |
| 5.6 | Battery usage screen after a normal day | Nudge should be a negligible share (note the %) |

## 6. Privacy and data safety
| # | Test | Expected |
|---|---|---|
| ★6.1 | Phone Settings → Apps → Nudge (debug) → Permissions / "All permissions" | **No** internet/network access listed |
| ★6.2 | Privacy → Pause all detection, friend sends a request | Nothing created. Unpause |
| ★6.3 | Turn **sender names off**, detect a new message | Title uses "them", no name shown anywhere |
| 6.4 | *Deferred until stable signing.* Install the next dev build over the top | May need an uninstall (expected for dev builds). If it installs, reminders should still be there |
| 6.5 | Privacy → Delete completed and dismissed | Only finished items removed |
| ★6.6 | Privacy → Delete all Nudge data (do this **last**) | Back to onboarding, no reminders. WhatsApp/Gmail messages untouched |

## Optional
| # | Test | Expected |
|---|---|---|
| 7.1 | Settings → turn on bubble → allow overlay | Movable bubble with today's count; tap opens Nudge; drag to bottom hides it |
| 7.2 | Dark mode | Everything readable |

---
**Send back:** the filled-in tables, or just the ❌ rows with notes. Critical failures get fixed before merge; everything else feeds the Phase 2 backlog.
