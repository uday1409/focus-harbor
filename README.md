# Focus Harbor

A calm, offline Android Home launcher with saved app presets, optional intentions,
timed or open-ended sessions, and an optional ten-or-more-word exit challenge.
Inspired by distraction-free spaces, built
without root, device-admin locks, accessibility interception, or a backend.

**Important:** this limits your Home screen, not Android itself. Notifications,
Recents, settings, links, or another app can still reach other apps. Normal
in-app exit follows your pre-session word-challenge choice; OS recovery and uninstall remain
possible. It is not a tamper-proof version of OnePlus Zen Space.

**Download:** [Last published evaluation APK and SHA-256 checksum (1.1.0)](https://github.com/uday1409/focus-harbor/releases/tag/v1.1.0-preview).
Version 1.2.0, described below, has been built locally but is not yet published.
Publishing is awaiting confirmation of the repository's intended visibility.

## Use it

1. Manually install the evaluation APK after reviewing
   [safety and recovery](docs/safety-and-recovery.md). Android 8.0+ is required.
2. Open **Focus Harbor**, tap **Set as Home**, and select it in Android's chooser.
3. Choose or create a **Work**, **Study**, **Sleep**, or **Time with family** preset
   and select at least one app with **Choose / open apps**. Each preset saves its
   own selection locally; you can rename or delete presets before focus.
4. Optionally enter **What are you focusing on?** and choose a duration, or leave
   the session open-ended. The intention is shown on Home and cleared on exit.
   Optionally enable
   **Require 10+ words to leave focus**. It is off by default; your choice is
   remembered and can be changed before the next session. Phone access is separate.
5. Read the safety note, accept it, and tap **Enter focus**.
6. Tap **Leave focus** to return to setup. With the challenge off, exit is
   immediate. With it on, write **any text with at least 10 words** and tap
   **End focus**. Exactly 10 words or any larger number is accepted; no text
   needs to match a preset. The question is **What do you need your phone for?**
7. When a timer reaches zero, Home offers **Finish** or **Keep going**. Finish
   follows the same selected exit policy; Keep going adds time or becomes
   open-ended. Focus never ends or launches other apps automatically.

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
