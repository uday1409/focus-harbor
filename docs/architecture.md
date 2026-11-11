# Architecture

```text
MainActivity
  -> Compose UI (FocusHarborApp)
  -> FocusViewModel (immutable StateFlow)
       -> AppCatalog -> Android LauncherApps, current user only
       -> FocusStore -> Preferences DataStore, atomic edit
            -> ExitPhrase (pure Kotlin rules)
```

`MainActivity` declares Android's HOME category. It neither grants itself the
Home role nor modifies other applications. A user-initiated Android role chooser
(API 29+) or Home settings screen (API 26-28) performs that selection.
The role is rechecked on resume and immediately before starting a session.

`AppCatalog` uses `LauncherApps.getActivityList` for the current user, excludes
this app, deduplicates packages, loads Android-provided labels/icons off the main
thread, and sorts by locale. It does not enumerate every installed package and
does not request `QUERY_ALL_PACKAGES`. Resume refreshes account for uninstall,
disable, and profile availability changes. Failed launches are reported.

`FocusStore` owns the persisted package allowlist, normalized exit text, active
flag, and wall-clock start timestamp. All fields are committed together.
Starting while active cannot replace the configuration. Ending focus compares
the entered text with the stored text inside the same atomic edit that clears
the active flag. Reconstructing UI state does not create an exit path.

`FocusViewModel` owns drafts, role/catalog refresh, serialized user actions, and
explicit errors. Read failures do not emit empty preferences. Failed writes are
shown without pretending that the requested transition succeeded.

The UI has setup, an app picker with normal-mode app launch, a focus grid, a
read-only recovery guide, and an exit challenge. During focus, only the matching
text transaction returns to setup. Back opens or closes the challenge.
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
the chosen phrase/session out of the app's Android backup rules. Rooted devices,
debugging access, keyboards, and allowed applications are outside the trust
boundary. The evaluation APK is debuggable and must not hold secrets.
