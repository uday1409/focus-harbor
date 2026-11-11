# Focus Harbor

A calm, offline Android Home launcher with a custom app allowlist and an
intentional ten-word exit challenge. Inspired by distraction-free spaces, built
without root, device-admin locks, accessibility interception, or a backend.

**Important:** this limits your Home screen, not Android itself. Notifications,
Recents, settings, links, or another app can still reach other apps. Normal
in-app exit requires your text; OS recovery and uninstall deliberately remain
possible. It is not a tamper-proof version of OnePlus Zen Space.

## Use it

1. Manually install the evaluation APK after reviewing
   [safety and recovery](docs/safety-and-recovery.md). Android 8.0+ is required.
2. Open **Focus Harbor**, tap **Set as Home**, and select it in Android's chooser.
3. Choose your own text of exactly **10 words** and select at least one app with
   **Choose / open apps**. Phone access is available separately.
4. Read the safety note, accept it, and tap **Enter focus**.
5. To return to setup, tap **Leave focus**, retype the displayed text, and tap
   **End focus**. Case and punctuation must match; extra whitespace is ignored.

Focus persists when the launcher is recreated or reopened. Configuration is
locked during an active session. There is no timer-based auto-exit.
The text is visible, locally stored friction, not a password; do not use secrets.

To restore your original launcher, use Android **Settings > Apps > Default apps >
Home app**. You can uninstall Focus Harbor at any time through Android.

## Build

Set `JAVA_HOME` to JDK 17 and `ANDROID_HOME` to an SDK with platform 36 and Build
Tools 35.0.0, then run in PowerShell 7:

```powershell
.\scripts\check.ps1
```

The checked/exported APK and its SHA-256 are in `dist`. It is a **debug-signed
personal-evaluation APK**, not a production release. Generated binaries and
keystores stay out of Git. See [build instructions](docs/build.md) for native
Gradle commands, dedicated-emulator checks, and GitHub publishing.

## Agent-ready development

Start with [`AGENTS.md`](AGENTS.md). Task runbooks follow the open Agent Skills
format in [`.agents/skills`](.agents/skills); observable behavior lives in
[`specs/focus-mode.md`](specs/focus-mode.md). The
[agent guide](docs/agent-guide.md) explains every folder and the limits of
automatic client discovery.

Architecture: Kotlin, Jetpack Compose Material 3, lifecycle-aware `StateFlow`,
and atomic Preferences DataStore. No network permission, account, analytics,
runtime AI, broad package query, or background blocking service is used.
Only the current Android profile is supported.

No open-source license has been selected. Keep the repository private until
its owner deliberately chooses a licensing/distribution policy.
