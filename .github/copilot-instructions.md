# Focus Harbor

Read the root `AGENTS.md` for the canonical build commands and safety contract.
Read `specs/focus-mode.md` before changing behavior. Relevant runbooks are in
`.agents/skills/android-change/SKILL.md` and `.agents/skills/build-apk/SKILL.md`.

This is an offline, reversible Android Home launcher. Never add device-admin,
root, accessibility interception, overlays, VPNs, or system-wide blocking.
Keep the matching 10-word normal exit check inside the atomic DataStore edit.
Keep active configuration immutable and surface persistence/launch errors.
Only run connected tests on a dedicated emulator, never an unspecified device.
