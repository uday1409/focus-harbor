# Focus Harbor

Read the root `AGENTS.md` for the canonical build commands and safety contract.
Read `specs/focus-mode.md` before changing behavior. Relevant runbooks are in
`.agents/skills/android-change/SKILL.md` and `.agents/skills/build-apk/SKILL.md`.

This is an offline, reversible Android focus launcher. Never add device-admin,
root, overlays, usage-stats polling, VPNs, package disabling, or lock-task
pinning. The optional Strict App Guard (`FocusAppGuardService`) must remain
package-only (`canRetrieveWindowContent="false"`, `TYPE_WINDOW_STATE_CHANGED`
only), always exempt Phone/Emergency/IME keyboards/non-Recents SystemUI (preserving
Android Safe Mode recovery), and stop blocking immediately at `00:00`.
Open-ended sessions enforce the optional 10-or-more-word check inside DataStore
(any wording valid; never persist entered exit text). Strict Timebound sessions
(1–180 min max) block manual exit while `remainingMillis > 0` and unlock 1-tap
Finish or Keep going at `00:00`.
Keep active configuration immutable and surface persistence/launch errors.
Never proxy idle `CATEGORY_HOME` intents to another launcher; use Android's
native Home role/settings chooser for switching Home, and never let Back on
Focus Home end an active session.
Keep presets local and session snapshots immutable. Clear intention text on exit.
No break feature is included. Only run connected tests on a dedicated emulator,
never an unspecified device.
