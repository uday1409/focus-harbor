# Architecture

```text
MainActivity
  -> Compose UI (FocusHarborApp)
  -> FocusViewModel (immutable StateFlow)
       -> AppCatalog -> Android LauncherApps, current user only
       -> NormalLauncherCatalog -> current-profile exported Home activities
       -> HomeRequest -> resumed MainActivity -> explicit normal-Home intent
       -> FocusStore -> Preferences DataStore, atomic edit
            -> ExitText (pure Kotlin word counting)
            -> FocusPreset / FocusPlan / FocusTime (local configuration and timing)
```

`MainActivity` declares Android's HOME category. It neither grants itself the
Home role nor modifies other applications. A user-initiated Android role chooser
(API 29+) or Home settings screen (API 26-28) performs that selection.
The role is rechecked on resume and immediately before starting a session.
An inactive Home intent is distinct from app-icon entry. `onNewIntent` updates
the single-task activity's intent so Home can forward while the app icon still
opens setup, including after recreation.

`NormalLauncherCatalog` uses a narrow `MAIN`/`HOME` package-visibility query.
It excludes this package, disabled/unexported activities and inaccessible
permission-protected components. The user explicitly chooses a package/activity
once; DataStore saves it separately from presets and rejects edits during focus.
Legacy sessions with no choice are not reset or blocked from normal exit.

Only a successful `ENDED` result schedules an exit handoff. Home requests also
re-read the persisted session, re-query the target, then recheck idle state after
the query. The resumed activity consumes each request once before launching the
explicit external Home component. Unknown storage, active focus, failed exit
writes and stale requests never forward. No handoff is triggered by mere resume.
A short monotonic handoff guard stops rapid launcher bounce loops; errors pause
automatic retries until explicit selection/retry. Missing/denied Home targets
show launcher selection and recovery without changing the saved ended state.
Android's default Home role is not reassigned, and components are never disabled.

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

Timed sessions save both an epoch deadline and an elapsed-realtime deadline
with the Android boot counter. `remainingMillis` uses the monotonic deadline
on the same boot and the epoch deadline after reboot. Missing boot-counter
support is logged explicitly and uses the epoch fallback. Time reaching zero is
derived state, not an exit mutation. Keep going changes only the deadline after
the current timer expires; it retains the original start, apps, intention and
exit choice. UI ticking stops while Home is not visible; no alarms, services,
notifications, automatic app launches, or extra permissions are involved.

The DataStore migration removes legacy custom phrases. An active legacy session
retains a word-count gate, without requiring its old phrase. Idle legacy settings
default the new optional choice off. Explicit new-format choices are preserved.

`FocusViewModel` owns drafts, role/catalog refresh, serialized user actions, and
explicit errors. Read failures do not emit empty preferences. Failed writes are
shown without pretending that the requested transition succeeded.

The UI has setup, an app picker with normal-mode app launch, a focus grid, a
read-only recovery guide, and an optional exit challenge. An enabled challenge
accepts any 10 or more words; otherwise Leave focus or Back ends focus immediately.
When enabled, Back opens or closes the challenge without ending the session.
App launch goes through `LauncherApps.startMainActivity` and checks the allowlist.
The phone button uses `ACTION_DIAL`, not `ACTION_CALL`.

## Dependencies and trust

Kotlin + Compose Material 3 are the UI foundation; AndroidX lifecycle and
Preferences DataStore provide lifecycle-aware state and durable storage.
There is no backend, network SDK, AI runtime, background service, device
administrator, or accessibility service. AndroidX supplies its own transitive
native libraries; the app does not contain custom native code.

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
