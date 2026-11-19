# Architecture

```text
MainActivity
  -> Compose UI (FocusHarborApp)
  -> FocusViewModel (immutable StateFlow)
       -> AppCatalog -> Android LauncherApps, current user only
       -> FocusStore -> Preferences DataStore, atomic edit
            -> ExitText (pure Kotlin word counting)
            -> FocusPreset / FocusPlan / FocusTime (local configuration and strict timing)
FocusAppGuardService (optional package-only AccessibilityService)
  -> FocusStore.sessions (in-memory Flow) + AppGuardPolicy (pure Kotlin package rules)
```

`MainActivity` declares Android's HOME category. It neither grants itself the
Home role nor modifies other applications, and it never proxies idle Home
intents to another launcher. A user-initiated Android role chooser (API 29+) or
Home settings screen (API 26-28) performs Home selection. When Focus Harbor is
not yet Home, **Set Home & enter focus** requests the Home role and starts focus
once the role is held; when focus is off and Focus Harbor still holds Home, the
top card opens `Settings.ACTION_HOME_SETTINGS` so the user can restore their
usual launcher natively. If the user enables **Open Home switcher when leaving
focus**, a buffered one-shot event opens `Settings.ACTION_HOME_SETTINGS` once
after `endSession` succeeds while Focus Harbor holds Home, without re-triggering
on return or recreation. The role is rechecked on resume and immediately before
starting a session.

`AppCatalog` uses `LauncherApps.getActivityList` for the current user, excludes
this app, deduplicates packages, loads Android-provided labels/icons off the main
thread, and sorts by locale. It does not enumerate every installed package and
does not request `QUERY_ALL_PACKAGES`. Resume refreshes account for uninstall,
disable, and profile availability changes. Failed launches are reported.

`FocusStore` owns the persisted package allowlist, optional exit-challenge choice, active
flag, and wall-clock start timestamp. All fields are committed together.
Version 1.2 also snapshots the preset name, optional intention, and timed deadline.
Presets use independent named app sets; only idle configuration can be edited.
The first initialization seeds four presets and carries the old app selection
into Work without modifying an active legacy session.
Starting while active cannot replace the configuration. Ending focus checks
the stored choice inside the atomic edit: any text with at least 10 words is
required only when enabled. No preset or comparison text exists, and entered
text is never persisted. Reconstructing UI state does not bypass an enabled gate.
The intention is different from exit text: it is persisted only while active so
Home can display it after process recreation, and removed atomically on exit.

Timed sessions (1–180 minutes max) are **Strict Timebound**: `FocusStore` saves
the planned `durationMinutes`, an epoch deadline, and an elapsed-realtime
deadline with the Android boot counter. `remainingMillis` uses the monotonic
deadline on the same boot, falls back to the epoch deadline after reboot, and
clamps into `0..(durationMinutes * 60_000)` so backward clock jumps can never
inflate a strict timer. While `remainingMillis > 0`, `endSession` rejects early
exit (`STRICT_TIMER_ACTIVE`) and the UI hides `Leave focus`. When the countdown
reaches `00:00`, **Finish** unlocks in one tap and **Keep going** sets a new
strict deadline (or switches to open-ended).

`FocusAppGuardService` is an optional, event-driven `AccessibilityService`
configured with `typeWindowStateChanged` and `canRetrieveWindowContent="false"`.
It never inspects window hierarchies or screen text. It collects `FocusStore`
state in memory and delegates every decision to pure-Kotlin `AppGuardPolicy`:
blocking occurs only when `session.isActive && session.strictAppGuard` and
`remainingMillis` is `null` or `> 0`. Non-allowlisted foreground packages and
Recents bounce back via `performGlobalAction(GLOBAL_ACTION_HOME)` (or
`MainActivity` fallback if Home is not held), while Phone dialers, emergency/call
UI, enabled IME keyboards, and non-Recents `com.android.systemui` are always
exempted.

## Dependencies and trust

Kotlin + Compose Material 3 are the UI foundation; AndroidX lifecycle and
Preferences DataStore provide lifecycle-aware state and durable storage.
There is no backend, network SDK, AI runtime, overlay, usage-stats poller, or
device administrator. The optional `FocusAppGuardService` uses Android's standard
`BIND_ACCESSIBILITY_SERVICE` service protection with window-content retrieval
disabled. AndroidX supplies its own transitive native libraries; the app does
not contain custom native code.

Storage JVM tests use Robolectric API 28 so Android's actual API-26+ file-move
branch runs against real temporary files. Android stub JARs otherwise report
SDK 0 and exercise an obsolete file-rename branch that cannot replace files
on Windows. Do not mask that failure by deleting destination files or replacing
all persistence tests with mocks. DataStore 1.3.0 includes the upstream modern
atomic-move and concurrent-update fixes.

Gradle's distribution checksum and immutable CI action commit pins reduce
tooling substitution risk. Maven dependencies are version-pinned through
`gradle/libs.versions.toml`; a Compose BOM pins compatible Compose modules.
These controls are not a claim of fully hermetic or independently certified
supply-chain security.

App data is private to the application, not encrypted by an app-specific key.
OS sandbox/encryption behavior applies. Backup/device-transfer exclusions keep
the session settings out of the app's Android backup rules. Rooted devices,
debugging access, keyboards, and allowed applications are outside the trust
boundary. The evaluation APK is debuggable and must not hold secrets.
