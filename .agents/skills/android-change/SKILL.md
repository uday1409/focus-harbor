---
name: android-change
description: Implement or modify Focus Harbor Android launcher features, app allowlisting, Strict App Guard, Strict Timebound or Open-ended focus persistence, or the optional ten-or-more-word exit challenge while preserving reversible device-safe behavior.
compatibility: JDK 17 and Android SDK platform 36 with Build Tools 35.0.0; a dedicated emulator is required for UI tests.
---

# Android feature change

1. Read the root [agent contract](../../../AGENTS.md),
   [focus specification](../../../specs/focus-mode.md), and
   [safety guide](../../../docs/safety-and-recovery.md).
2. Identify the acceptance criterion being changed. Preserve the non-device-owner
   safety boundary and Android Safe Mode hardware escape hatch.
3. Keep word counting in `ExitText`, package-guard rules in `AppGuardPolicy`,
   and session enforcement inside `FocusStore`'s atomic edits:
   - Open-ended sessions: enabled word challenge means any 10+ words; disabled
     means immediate exit. Never match a preset or save entered text.
   - Strict Timebound sessions (1–180 min): reject manual exit while
     `remainingMillis > 0` (`STRICT_TIMER_ACTIVE`), clamp `remainingMillis`
     monotonically, and unlock 1-tap Finish or Keep going at `00:00`.
   - Strict App Guard (`FocusAppGuardService`): must stay package-only
     (`canRetrieveWindowContent="false"`), stop immediately at `00:00`, and
     always exempt dialers, emergency/in-call UI, enabled IMEs, and non-Recents
     SystemUI.
   - Never proxy idle `CATEGORY_HOME` intents to other launchers or let Back on
     Focus Home terminate an active session; rely on Android's native Home chooser.
4. Update the smallest implementation and regression tests. Follow existing
   Compose state and Android resource patterns. Do not introduce dangerous
   permissions, overlays, or usage-stats polling.
   Presets must not mutate active snapshots. Timers must survive reopening and
   never auto-exit. Intentions are stored only during the active session and
   cleared by the same exit transaction.
5. Run the wrapper's focused unit tests, `:app:lintDebug`, and `:app:assembleDebug`.
   For UI/session/lifecycle work, run `:app:connectedDebugAndroidTest` on a
   dedicated emulator only. Run commands from the repository root; see the
   [build guide](../../../docs/build.md) for serial isolation.
6. Update the acceptance criteria and relevant architecture notes if behavior
   changes. Report the artifact path, known platform limitations, and exactly
   which checks ran; never imply universal device-safety certification.

If a request requires unbypassable blocking, stop and explain that it conflicts
with this project's reversible, non-device-owner safety model.
