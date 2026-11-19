# Focus Harbor

A calm, offline Android focus launcher inspired by Zen Space, featuring 1-tap
app presets, **Open-ended** or **Strict Timebound** (1–180m) modes, an optional
10+ word exit challenge, and an optional **Strict App Guard** (package-only
`AccessibilityService`, `canRetrieveWindowContent="false"`) that blocks Recents
and indirect non-allowlisted app launches while preserving Phone calls and
Android Safe Mode recovery.

**Download:** [Focus Harbor 1.6.0 evaluation APK and SHA-256 checksum](https://github.com/uday1409/focus-harbor/releases/tag/v1.6.0-preview).
Open the release's **Assets** section to download the debug-signed preview.

## Use it

1. Manually install the evaluation APK after reviewing
   [safety and recovery](docs/safety-and-recovery.md). Android 8.0+ is required.
2. Tap a **Work**, **Study**, **Sleep**, or **Time with family** preset chip
   and select at least one app with **Choose / open apps**.
3. Tap **Open-ended** (leave anytime, with optional **Require 10+ words to leave
   focus**) or a **Strict Timebound** chip (`15m`, `25m`, `45m`, `60m`, or
   `Custom…` up to 180m — manual exit stays locked until `00:00`).
4. Optionally enable **Block non-allowed apps & Recents** for Zen Space-style
   blocking of browser links and Recents. A guided one-time dialog opens:
   - **1. Open App Info** → tap `⋮` → **Allow restricted settings** (for
     sideloaded APKs on Android 13+) and set Battery to **Unrestricted**.
   - **2. Open Accessibility Settings** → turn **Focus Harbor** on (package-only
     check; never reads screen text).
5. Accept the one-time safety note and tap **Set Home & enter focus** (or
   **Enter focus**).
6. When leaving focus (or tapping 1-tap **Finish** at `00:00`), **Open Home
   switcher when leaving focus** (on by default) opens Android's Home chooser
   automatically so you can switch back to your normal launcher in one tap.

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
atomic Preferences DataStore, and an optional package-only `AccessibilityService`
(`canRetrieveWindowContent="false"`). No network permission, account, analytics,
runtime AI, or broad package query is used. Only the current Android profile is
supported.

No open-source license has been selected. Do not change repository visibility
or add a licensing/distribution policy without the owner's approval.
