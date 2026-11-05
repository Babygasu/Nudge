# Known limitations (Phase 1)

Honest list of what this build does **not** do or may get wrong. Updated each milestone.

## What Nudge can see
- **Only notifications.** Nudge sees what an app puts in its notification: usually sender, a preview, sometimes several recent messages. It sees nothing from muted chats, chats open on screen, apps whose notifications are off, or messages that arrive while the phone has no connection (they haven't reached the phone yet).
- **Your own messages are mostly invisible.** Promises you make ("I'll send it tomorrow") are only seen if the app shows your reply inside the notification (some do after inline replies). Otherwise, use **Share → Nudge**.
- **Long messages and emails are truncated** to the preview the app provides.
- **Media messages** ("📷 Photo", voice notes) contain no text to analyse.
- **Missed calls** depend on how your phone's dialer words its notification. Tested patterns: "Missed call", "Missed voice call". Other wording may not be recognised.
- Detection pauses if Android disconnects the listener (it reconnects automatically; battery-saving on some phones can delay this).

## Intelligence (rule-based, English)
- The on-device engine understands English phrasing only. Other languages are not detected yet.
- It uses linguistic patterns (requests, promises, blocking, chasing, deadlines), not an AI model. It will miss unusual phrasing and sometimes misfire; every detected reminder can be edited or marked "Not a reminder".
- Pronouns aren't resolved: "I'll send it" becomes "Send it", plus the person's name when known.
- Ambiguous dates are guessed: "next Friday" means the coming Friday; "3:30" without am/pm means afternoon; numeric dates are day/month except on US-region phones.
- Corrections are recorded (as content-free events) but **do not yet change future detection**. That's Phase 2.
- No Personal Context Profile yet (important people and projects). That's Phase 3. Every sender is treated equally for now.

## Reminders and delivery
- If you refuse **on-time reminders** (exact alarms), Android may deliver reminders several minutes late, more so in battery-saving mode.
- Some manufacturers (Samsung, Xiaomi, Huawei, OnePlus) can stop background apps despite Android's rules. Setting battery usage to *Unrestricted* helps.
- After a phone restart, reminders are re-armed when Android delivers the boot broadcast, typically within a minute.
- Recurring reminders are not supported yet.

## Privacy and security
- The text of a captured notification exists only in app memory while it's analysed (milliseconds). Java/Kotlin strings can't be wiped from memory on demand; they're dropped and reclaimed by the runtime. They are never written to disk, logs or backups.
- If Android kills Nudge in the middle of analysing a notification, that one notification is skipped (by design: we don't queue message text).
- Reminder titles are derived from message wording, so a title can contain part of what someone wrote (e.g. "Send Ama the report"). Titles are encrypted at rest.
- With sender names off, Nudge stores a keyed hash of the sender so duplicates can be detected. It can't be reversed without the device's Keystore key, which "Delete all data" destroys.
- Development builds use a temporary Android debug key that can change between builds. Updating may require an uninstall, which erases test reminders. They are for testing, not distribution.

## Not in Phase 1 (planned)
Google Calendar / phone calendar, Google Tasks, Microsoft To Do, Samsung Reminder (via To Do), optional cloud AI, Personal Context Profile, adaptive learning from corrections, home-screen widget, languages other than English.
