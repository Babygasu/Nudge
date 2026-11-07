# Play Protect block: findings and testing workflow

## What happened (2026-11-07)
Installing `nudge-dev-4.apk` from the browser on a Samsung phone showed:

> "App blocked to protect your device. This app can request access to sensitive data. This can increase the risk of identity theft or financial fraud." (only an **OK** button)

## Cause
This is Google Play Protect's **enhanced fraud protection**. Google piloted it in Singapore in 2024, then
extended it to Thailand, Brazil, India and, per Google, many more markets (most recently Malaysia, 2026).
When an app is installed from an **Internet-sideloading source** (a web browser, messaging app or file
manager), Play Protect checks whether it declares any of four capabilities that fraud apps abuse to read
one-time passwords or spy on the screen:

| Capability | In Nudge? |
|---|---|
| `RECEIVE_SMS` | No |
| `READ_SMS` | No |
| Notification listener (`BIND_NOTIFICATION_LISTENER_SERVICE`) | **Yes: full variant (core feature)** |
| Accessibility service | No |

Any match means the install is blocked outright, with no "Install anyway" option. The check is about the
**declared capability and install source**, not about what the app actually does. That's why an
honest app like Nudge is caught too. Nudge doesn't request any of the other three.

## Audit of build 4 (from the actual APK)
| Permission / component | Source | Needed? | Risk notes |
|---|---|---|---|
| Notification listener service | Nudge | Yes, for automatic detection | **Triggers the block** |
| `SYSTEM_ALERT_WINDOW` (overlay) | Nudge | Optional bubble | Overlays are abused by banking malware. Not on the blocking list, but now full-variant only |
| `FOREGROUND_SERVICE_SPECIAL_USE` | Nudge | Bubble only | Now full-variant only |
| `<queries>` for messaging apps | Nudge | Listing apps to choose from | Now full-variant only |
| `POST_NOTIFICATIONS` | Nudge | Delivering reminders | Normal |
| `SCHEDULE_EXACT_ALARM` | Nudge | On-time reminders | Normal, user-revocable |
| `RECEIVE_BOOT_COMPLETED` | Nudge + WorkManager | Restoring reminders after restart | Normal |
| `WAKE_LOCK`, `ACCESS_NETWORK_STATE`, `FOREGROUND_SERVICE` | WorkManager library | Background safety-net job | Normal-level. `ACCESS_NETWORK_STATE` only reads connectivity; the app still has no internet access |
| `DUMP`, `BIND_JOB_SERVICE` | WorkManager | *Not requested.* These protect WorkManager's own internal components | None |
| `INTERNET` | none | Explicitly removed | — |

No unnecessary sensitive permissions were found: no SMS, call log, contacts, location, accessibility,
`QUERY_ALL_PACKAGES` or install-packages. CI now **fails the build** if any of those ever appear.

## What we changed (without weakening Nudge)
Nudge now builds two variants from the same code:

| | **Nudge Core** (`nudge-core-dev-N.apk`) | **Nudge** (`nudge-full-dev-N.apk`) |
|---|---|---|
| App ID | `com.babygasu.nudge.core.debug` | `com.babygasu.nudge.debug` |
| Dashboard, manual reminders, snooze/done | ✅ | ✅ |
| Offline scheduling, restart recovery | ✅ | ✅ |
| Share → Nudge / paste (on-device understanding, duplicates, priorities) | ✅ | ✅ |
| Privacy Centre, delete data | ✅ | ✅ |
| Automatic detection from notifications and missed calls | — (component absent) | ✅ |
| Floating bubble | — | ✅ |
| Installs from the phone's browser | ✅ expected | ❌ blocked where enhanced fraud protection is active |

Both can be installed side by side. The full variant is unchanged; we did **not** remove or disguise the
notification listener. In the core variant the listener isn't hidden: it **doesn't exist**, which CI verifies
by inspecting the built APK.

## Testing notification monitoring safely
We won't ask anyone to disable Play Protect. Two legitimate routes:

### Route A: install over ADB from the laptop (recommended next)
ADB is Android's official developer channel. It isn't an "Internet-sideloading source", which is what
enhanced fraud protection targets. Google also keeps ADB exempt from the new developer-verification rules.
**To verify on your phone:** whether Play Protect still shows a warning for ADB installs. If it does, we'll document it.

You need only Google's free **SDK Platform-Tools** (about 10 MB). No Android Studio, and no USB cable thanks to
Wireless debugging (Android 11+):
1. Phone: **Settings → About phone → Software information** → tap **Build number** 7 times → enter PIN.
   **Developer options** now appears in Settings.
2. Laptop and phone on the **same Wi-Fi**. Phone: **Developer options → Wireless debugging → On →
   Pair device with pairing code**.
3. Laptop: download Platform-Tools for Windows from `developer.android.com/tools/releases/platform-tools`,
   unzip, open a terminal in that folder, then:
   - `adb pair <IP>:<pairing-port>` and type the 6-digit code
   - `adb connect <IP>:<port shown on the Wireless debugging screen>`
   - `adb install -r nudge-full-dev-N.apk`
4. Open Nudge and turn on notification access as usual. If Android shows "Restricted setting", use
   App info → ⋮ → Allow restricted settings.
5. When finished testing, turn **Wireless debugging off** (it's a powerful channel; leave it off when not in use).

Claude can run steps 3–4 from this laptop once Platform-Tools is installed and you've paired the phone.

### Route B: Google Play internal testing (before wider testers)
Upload the app to a Play Console **internal testing** track and install it from the Play Store. Play Store
installs aren't sideloads, so this block doesn't apply, and it exercises the real Play policy review
(prominent disclosure for notification access, Data safety form, privacy policy).
- One-time **US$25** Play Console registration plus identity verification (also needed for Android developer
  verification in 2027).
- Up to 100 internal testers, installs within minutes of upload.
- Recommended once Phase 2 is stable and before inviting anyone else to test.

### Not recommended
Disabling Play Protect, Samsung Auto Blocker or other protections, asking testers to do so, or
"obfuscating" the listener to evade detection. All of these undermine the trust Nudge is built on.

## Note on Samsung Auto Blocker
Samsung phones (One UI 6+) have a separate **Auto Blocker** that, when on, blocks *all* apps from unknown
sources with a different message. If Nudge Core also fails to install with a Samsung-branded message, tell us
the exact text before changing anything.

## Sources
- [Google Security Blog: piloting new ways to protect Android users from financial fraud (2024)](https://security.googleblog.com/2024/02/piloting-new-ways-to-protect-Android-users-from%20financial-fraud.html)
- [Google: enhanced fraud protection pilot in India](https://blog.google/intl/en-in/products/launching-enhanced-fraud-protection-pilot-in-india/)
- [Google: enhanced protection in Play Protect, Malaysia (2026)](https://blog.google/intl/en-my/company-news/technology/google-launches-enhanced-protection-in-google/)
- [Android Authority: Play Protect financial-fraud blocking](https://www.androidauthority.com/google-play-protect-financial-fraud-apps-3411554)
- [Google: Android developer verification](https://support.google.com/android-developer-console/answer/16561738)
