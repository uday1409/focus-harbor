# Focus Harbor

Read the root `AGENTS.md` for the canonical build commands and safety contract.
Read `specs/focus-mode.md` before changing behavior. Relevant runbooks are in
`.agents/skills/android-change/SKILL.md` and `.agents/skills/build-apk/SKILL.md`.

This is an offline, reversible Android Home launcher. Never add device-admin,
root, accessibility interception, overlays, VPNs, or system-wide blocking.
Keep the optional 10-or-more-word exit check inside the atomic DataStore edit.
Any wording is valid; never match a preset or persist entered exit text.
An unchecked pre-session option permits immediate exit without text.
Keep active configuration immutable and surface persistence/launch errors.
Keep presets local and session snapshots immutable. Clear intention text on exit.
Timed sessions offer Finish / Keep going on Home without automatic exit or
bypassing the existing optional word challenge. No break feature is included.
Only run connected tests on a dedicated emulator, never an unspecified device.
