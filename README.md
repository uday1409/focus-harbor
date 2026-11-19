# Focus Harbor

A calm, offline Android Home launcher with saved app presets, optional intentions,
timed or open-ended sessions, and an optional ten-or-more-word exit challenge.
Inspired by distraction-free spaces, built
without root, device-admin locks, accessibility interception, or a backend.

**Important:** this limits your Home screen, not Android itself. Notifications,
Recents, settings, links, or another app can still reach other apps. Normal
in-app exit follows your pre-session word-challenge choice; OS recovery and uninstall remain
possible. It is not a tamper-proof version of OnePlus Zen Space.

**Download:** [Focus Harbor 1.6.0 evaluation APK and SHA-256 checksum](https://github.com/uday1409/focus-harbor/releases/tag/v1.6.0-preview).
Open the release's **Assets** section to download the debug-signed preview.

## Use it

1. Manually install the evaluation APK after reviewing
   [safety and recovery](docs/safety-and-recovery.md). Android 8.0+ is required.
2. Choose or create a **Work**, **Study**, **Sleep**, or **Time with family** preset
   and select at least one app with **Choose / open apps**. Each preset saves its
   own selection locally; you can rename or delete presets before focus.
3. Optionally choose a duration (or keep it open-ended), enter **What are you
   focusing on?**, and optionally enable **Require 10+ words to leave focus**
   or **Open Home switcher when leaving focus** (both off by default and
   remembered between sessions).
4. Accept the safety note once, then tap **Set Home & enter focus** (or **Enter
   focus** if Focus Harbor is already your Home app) and choose Focus Harbor in
   Android's prompt. Future sessions remember the safety note for quick entry.
5. Tap **Leave focus** to end the session. With the challenge off, exit is one
   tap; accidental Back swipes on Focus Home never end focus. With the challenge
   on, answer **What do you need your phone for?** with **any 10+ words** and
   tap **End focus**. If **Open Home switcher when leaving focus** is enabled,
   Android's Home app setting opens automatically; otherwise tap **Switch to
   normal Home** at the top of the screen whenever you are ready.
6. In a timebound session (1–180 min), manual exit stays locked until `00:00`.
   When the timer reaches zero, Home offers 1-tap **Finish** or **Keep going**.
   Focus never ends or launches other apps automatically.

Focus persists when the launcher is recreated or reopened. Configuration is
locked during an active session. Timers resume from persisted deadlines; the
completion prompt appears while on Home or when you return, not as a background
popup. No break feature, alarm modification, or automatic DND is included.
Entered exit text is never saved; intentions are saved only during their active
session. Do not enter secrets in either field.

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

No open-source license has been selected. Do not change repository visibility
or add a licensing/distribution policy without the owner's approval.
