# Signing setup: keeping your data across updates

## Why it matters
Android only installs an update over an existing app if both APKs are signed with the **same
certificate**. If the certificate changes, Android refuses ("App not installed, package conflicts").
The only way forward is then to uninstall, which **erases all reminders**.

So every Nudge build must be signed with one project key that never changes.

## How it works
| Piece | Where it lives | Secret? |
|---|---|---|
| Keystore `nudge-debug.p12` (private key + certificate) | Your laptop: `C:\Users\<you>\.nudge-signing\`, plus GitHub secret `NUDGE_DEBUG_KEYSTORE_B64` | **Yes** |
| Keystore password | Your laptop: `.nudge-signing\password.txt`, plus GitHub secret `NUDGE_DEBUG_KEYSTORE_PASSWORD` | **Yes** |
| Key alias `nudgedebug` | Workflow file | No |
| Certificate SHA-256 fingerprint `752c4343…4af34f` | Workflow file (`EXPECTED_CERT_SHA256`) | No: it's public, like a website certificate |

On every push, CI:
1. **Fails immediately** if either secret is missing, so no APK with a throwaway key can be published.
2. Decodes the keystore into the runner's temporary folder, builds, then **deletes it**.
3. Runs `apksigner verify` and **fails** unless the APK's certificate equals `EXPECTED_CERT_SHA256`.
4. Only then publishes the APK to Releases. The fingerprint is shown in the release notes.

GitHub masks secret values in logs, and secrets are never available to pull requests from forks.

Safeguards in the repo:
- `.gitignore` blocks `*.p12`, `*.jks`, `*.keystore`, `*.pem`, `signing/` and similar.
- The key folder is outside the repository (`~/.nudge-signing`), so it can't be committed by accident.
- Audited 2026-11-05: no keystore, password or private key appears anywhere in the Git history.

## One-time setup (5 minutes)

Run these in **PowerShell** on your laptop. Each command reads the secret from a local file and sends it
straight to GitHub over HTTPS, so the value is never shown on screen or written to shell history.

**1. Check you're logged in to the right GitHub account**
```powershell
gh auth status
```
It should say `Logged in to github.com account Babygasu`.

**2. Upload the keystore**
```powershell
gh secret set NUDGE_DEBUG_KEYSTORE_B64 --repo Babygasu/Nudge --body ([Convert]::ToBase64String([IO.File]::ReadAllBytes("$HOME\.nudge-signing\nudge-debug.p12")))
```

**3. Upload the password**
```powershell
gh secret set NUDGE_DEBUG_KEYSTORE_PASSWORD --repo Babygasu/Nudge --body (Get-Content "$HOME\.nudge-signing\password.txt" -Raw).Trim()
```

**4. Confirm both exist** (shows names and dates, never values)
```powershell
gh secret list --repo Babygasu/Nudge
```

**5. Back up the key.** Put `nudge-debug.p12` and the password into a password manager (as a secure note
with an attachment) or onto an encrypted USB drive. If the laptop is lost **and** the GitHub secrets are
deleted, the key is gone, and the next install would need an uninstall. Don't email it, put it in cloud
folders unencrypted, or paste it into chat.

Alternative to steps 2–3: GitHub → repository **Settings → Secrets and variables → Actions → New repository secret**,
pasting the values by hand. The commands above avoid copying secrets through the clipboard.

## If the key is ever compromised or lost
For debug builds the impact is limited: someone could build an APK that installs over your test app.
1. Generate a new key, update both secrets, and change `EXPECTED_CERT_SHA256` in the workflow.
2. On the phone, uninstall Nudge (debug) once and install the new build. Reminders are lost once.

## Release signing (later)
The Play Store / production release will use a **separate** release key (or Play App Signing), set up in
Phase 4. It will never be the debug key.

## Other things that protect your data across updates
- The app ID (`com.babygasu.nudge.debug`) never changes.
- `versionCode` is the CI run number, so every build is newer than the last.
- The database encryption key is in the Android Keystore and survives updates. It is deleted only when the app is uninstalled.
- Room is **not** configured to wipe data on schema changes. Any future database change must ship a migration; without one, the app would refuse to open the database rather than erase it.
- Uninstalling the app, or "Clear storage" in Android settings, erases reminders. Android backups are deliberately off, for privacy.
