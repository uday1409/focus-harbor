---
name: android-change
description: Implement or modify Focus Harbor Android launcher features, app allowlisting, focus persistence, or the optional ten-or-more-word exit challenge while preserving reversible device-safe behavior.
compatibility: JDK 17 and Android SDK platform 36 with Build Tools 35.0.0; a dedicated emulator is required for UI tests.
---

# Android feature change

1. Read the root [agent contract](../../../AGENTS.md),
   [focus specification](../../../specs/focus-mode.md), and
   [safety guide](../../../docs/safety-and-recovery.md).
2. Identify the acceptance criterion being changed. Preserve the distinction
   between a limited Home screen and system-wide enforcement.
3. Keep word counting in `ExitText` and enforce the stored optional challenge
   inside `FocusStore`'s atomic edits. Enabled means any 10 or more words;
   disabled means immediate exit. Never match a preset, save entered text, or
   let active-session callbacks change the stored choice.
4. Update the smallest implementation and regression tests. Follow existing
   Compose state and Android resource patterns. Do not introduce new privileged
   permissions or background monitors.
5. Run the wrapper's focused unit tests, `:app:lintDebug`, and `:app:assembleDebug`.
   For UI/session/lifecycle work, run `:app:connectedDebugAndroidTest` on a
   dedicated emulator only. Run commands from the repository root; see the
   [build guide](../../../docs/build.md) for serial isolation.
6. Update the acceptance criteria and relevant architecture notes if behavior
   changes. Report the artifact path, known platform limitations, and exactly
   which checks ran; never imply universal device-safety certification.

If a request requires unbypassable blocking, stop and explain that it conflicts
with this project's reversible, non-device-owner safety model.
