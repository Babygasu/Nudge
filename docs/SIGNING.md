# APK signing

## Now: development builds (Phase 1–3 testing)
- CI builds with Android's **standard debug signing**. The Android build tools generate a debug key automatically on
  each GitHub runner. **No signing secrets, passwords or keystores** are stored in GitHub or in this repository.
- Because each runner can generate a different debug key, **builds may have different certificates**. Installing a
  newer build over an older one can then fail ("App not installed" / "package conflicts"). The fix is to uninstall
  *Nudge (debug)* first, which **erases its local test data**. This was accepted for early development (D-006).
- Each release lists its debug certificate's SHA-256 fingerprint (public information), so you can tell whether two builds share a key.
- CI checks that every APK is validly signed with an Android debug certificate before publishing it.

## Workflow security
- Default token permission is **read-only** (`contents: read`). The build job runs repository and pull-request code,
  and checks out with `persist-credentials: false`, so the token isn't left on disk for later steps.
- A separate **publish** job is the only part with `contents: write`. It runs only for pushes to this repository,
  never for pull requests, checks out no code, and only uploads the already-built APK to a pre-release.
- Pull requests from forks get a read-only token and no secrets (GitHub's default), and publish nothing.

## Later: stable signing (deferred)
Before stable releases (Phase 4, or earlier if updates without data loss become important) we will:
1. Create a dedicated signing key and keep it out of the repository.
2. Store it as encrypted GitHub secrets, used only by a release workflow.
3. Have CI verify every release against the expected certificate fingerprint.
4. For the Play Store, use Play App Signing with a separate upload key.

A local key created during the earlier attempt still exists on the developer laptop in
`%USERPROFILE%\.nudge-signing` (outside the repository). It isn't used by any workflow and was never committed.
It can be deleted, or kept for the stable-signing step.
