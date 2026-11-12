# Focus Harbor: agent contract

## Read first

This is an offline Android Home launcher, not a kiosk, device manager, security
boundary, or system-wide app blocker. Read `specs/focus-mode.md` and
`docs/safety-and-recovery.md` before changing focus behavior.

## Non-negotiable invariants

- Never add root, device-owner/admin, accessibility interception, overlays,
  usage-access monitoring, VPNs, package disabling, or lock-task behavior.
- Keep Android recovery, uninstallation, the notification shade, Recents, and
  the device's emergency-call mechanisms untouched.
- The exit challenge is optional before focus, off by default. When enabled,
  any text with at least 10 words permits exit; no preset exit text or match is required.
  When disabled, exit immediately. Enforce the stored choice atomically in DataStore.
- An active session's exit choice and allowlist cannot be edited or overwritten.
- Save a user-chosen normal launcher locally; never silently change Android's
  default Home, disable launcher components, or guess a replacement launcher.
- Return to that launcher only after a successful saved exit or an inactive Home
  intent. Never forward while focus is active, storage is unknown, or saving fails.
  App-icon entry must still open setup while idle. Revalidate the external Home
  component, exclude this package, and stop failed/repeated handoff loops with
  visible launcher selection and recovery; never retry just because of resume.
- Presets are local app selections. Editing a preset must never rewrite an
  active snapshot. Keep at least one preset and preserve legacy selections.
- Intentions are optional, local to an active session, and removed on exit.
  Do not log them or retain an intention/reason history.
- Timed expiry never ends focus, opens apps, or bypasses the selected exit
  challenge. Finish uses the normal exit transaction; Keep going is explicit.
- Do not add breaks, alarms, notifications, or background services for timers.
  Offer completion when the user is on or returns to Home.
- Persist a complete session before showing success. Surface storage and
  app-launch failures; never reset an unreadable session to an idle default.
- Do not add analytics, networking, accounts, runtime AI, or cloud storage.
  Exit text is temporary friction, not a password; never persist it.
- Never install, clear data on, change Home on, or run tests against a physical
  device without the user's explicit device-specific permission.

## Map

- `app/src/main/java/dev/focusharbor/app/focus`: text policy and atomic storage.
- `app/src/main/java/dev/focusharbor/app/apps`: current-profile launcher catalog.
- `app/src/main/java/dev/focusharbor/app/home`: normal Home discovery and handoff guard.
- `FocusViewModel.kt`: Android integration, lifecycle refresh, explicit errors.
- `app/src/main/java/dev/focusharbor/app/ui`: Compose UI and exit challenge.
- `app/src/test`: text/state regression tests.
- `app/src/androidTest`: emulator UI acceptance tests.
- `specs`: desired behavior and acceptance criteria.
- `docs`: architecture, build instructions, safety, and agent onboarding.
- `.agents/skills`: task-specific, portable `SKILL.md` runbooks.

## Commands

Prerequisites: JDK 17, Android platform 36, Build Tools 35.0.0; set `JAVA_HOME`
and `ANDROID_HOME`. The checked-in Gradle wrapper pins and verifies Gradle 8.13.
SDK/dependency-freshness lint checks remain visible as informational advice;
weekly Dependabot updates handle upgrades. The preview intentionally targets
tested API 36, even when a runner has a newer SDK installed. Retarget only after
compatibility review and testing. Correctness/security warnings still fail.

Windows:

```powershell
.\scripts\check.ps1
# Only on a dedicated, already-running emulator:
.\scripts\check.ps1 -EmulatorSerial emulator-5586
```

Linux/macOS:

```sh
./gradlew --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
```

Before handing off a behavior change, run the focused unit tests, Android lint,
and APK build. Run the emulator acceptance tests for UI/session/lifecycle changes.
Do not claim an OEM/physical-device check or a GitHub upload you did not perform.

## Working style

Use Kotlin, Compose Material 3, immutable UI state, and the existing DataStore.
Keep business rules independent of Android; put visible text in `strings.xml`.
Use localized labels from Android, not a hardcoded app catalog. Only query
launchable apps in the current profile. Add focused regression cases for changed
behavior and update the acceptance table when semantics change.

Read `.agents/skills/android-change/SKILL.md` for feature work and
`.agents/skills/build-apk/SKILL.md` for artifact delivery. Skill discovery differs
between clients; these paths are explicit entry points, not magical agent jobs.

## Artifacts and credentials

Do not commit `local.properties`, SDKs, build outputs, APKs, keystores, tokens, or
exit texts from a real user. `dist` is an ignored local handoff folder; CI uses
artifacts. The debug APK is a personal-evaluation build, not a production release.
No open-source license or production signing policy has been selected.
