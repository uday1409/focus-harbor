# Focus mode specification

## Goal and boundary

Give a consenting Android user a calm Home screen containing only selected apps,
with an optional 10-or-more-word challenge for normal in-app exit.
The app is inspired by the idea of a distraction-free space; it does not use
OnePlus code, names, icons, or privileged OS features.

Supported: Android 8.0/API 26 and later, current Android profile only.
Compile/target API: 36. This is a Home-screen filter, not an app firewall.

## States

| State | Behavior | Transition |
| --- | --- | --- |
| Loading | No guessed session; phone and recovery guidance available | Read persisted preferences |
| Read error | Explicit failure; no setup, app launch, or new session | Retry storage or recover through Android |
| Setup | Choose a normal launcher, select apps/exit choice, select Focus Harbor as Home, accept the boundary | Successful atomic start |
| Focus | Only allowed installed apps shown; configuration hidden | Save an immediate exit if opted out; otherwise open the challenge |
| Exit challenge | Accept any fresh text with at least 10 words | Valid word count atomically ends focus; cancel stays focused |
| Time reached | Keep focus active and show Finish / Keep going on Home | Finish follows the selected exit policy; Keep going sets a new deadline or open-ended mode |
| Normal Home | Open the chosen launcher after a saved exit or inactive Home intent | App icon opens setup; active Home intents show focus instead |
| Launcher recovery | Focus stays off after exit; offer launcher selection, Android Home settings and safety guidance | An explicit valid selection is saved before retrying the handoff |

Process death, configuration changes, reopening from the app icon, Home intents,
and reboot do not intentionally clear an active persisted session. After reboot,
the session is read when Android next opens the launcher; there is no boot service.
The elapsed clock is informational and uses wall time. Timed deadlines use
monotonic elapsed time on the same boot, with a saved wall-clock deadline after
reboot or when the platform boot counter is unavailable.

## Returning to normal Home

- Choose a normal launcher once in setup, before starting a new session. Save
  its explicit package/activity locally and independently of presets. Allow
  changing it only while idle; do not assume the manufacturer launcher is wanted.
- Discover only enabled, exported, usable Home activities in the current profile
  with a narrow Home-intent query. Never target Focus Harbor or a guessed package.
- Leave focus, Back when opted out, and timed Finish hand off only after the
  normal exit transaction succeeds. A rejected or failed write never opens Home.
- A new Home intent while idle opens the saved launcher directly, including
  after activity recreation. Home while active stays on focus and never bypasses
  the exit choice. Verify saved state before every handoff; unknown storage is
  not an idle default.
- App-icon entry while idle shows setup, not the normal launcher. Handle new
  Home/app-icon intents on the existing single-task activity as well as cold entry.
- Handoffs run once per request while the activity is resumed, not on every
  resume, recomposition, timer tick, or settings refresh.
- Revalidate the chosen Home component immediately before opening it. Missing
  choices, unavailable components, discovery errors and denied launches surface
  launcher selection, Android Home settings and recovery. An already-ended
  session stays ended. An explicit replacement choice is saved before opening.
- Stop rapid repeated automatic handoffs to avoid a launcher bouncing back to
  this default Home. Pause automatic retries after errors until an explicit
  launcher selection or manual retry; do not create an automatic resume loop.
- Never silently change Android's default Home, disable components, modify
  another app, add privileged permissions, or interfere with uninstall/recovery.
  Forwarding makes idle Home feel normal; it is not a default-role reassignment.
- Legacy active sessions without a saved launcher still retain their apps and
  exit policy. They can end normally, then choose a launcher or use recovery.

## Presets, intention, and duration

- Seed editable Work, Study, Sleep, and Time with family presets on upgrade or
  first use. Put the previous saved app selection in Work; do not lose an active
  session or change its apps/exit policy during initialization.
- Each preset stores its own name and app set locally. Save picker changes when
  closing it, switching presets, or starting focus. Support creating, renaming,
  and confirmed deletion; retain at least one preset.
- Snapshot the selected preset name and apps when starting. Preset changes are
  blocked during focus and never mutate the active snapshot.
- An optional intention of up to 160 trimmed characters appears on focus Home.
  Persist it only for the active session so reopening works; delete it on normal
  exit. Never put intentions in preset history, logs, analytics, or cloud storage.
- Default to open-ended. Offer 15/25/45/60-minute choices and custom 1-1440 minutes.
  Remember the last start-duration choice independently of presets.
- Expiry never clears the active session, launches an app, changes Home, or
  bypasses the selected optional 10+ word exit policy.
- Show Finish / Keep going on Home when due, including after process death or
  reboot. No alarm, background popup, notification permission, or service is added.
- Keep going adds a newly selected duration from the time of the explicit action,
  or changes the ongoing session to open-ended. Preserve apps, intention, exit
  policy, and the original start time.
- The exit dialog asks "What do you need your phone for?" This is a wording
  change only: the optional 10+ word policy remains exactly as before.
- Temporary breaks are not part of this release. Alarms, DND, and recovery remain
  entirely under Android/user control, including for Sleep presets.

## Configuration and exit

- The challenge is optional, off by default on a new install. Its visible choice
  is remembered between sessions and can only be changed before starting focus.
- If enabled, accept any text with **at least 10 words**. Exactly 10, 11, and
  larger word counts are valid; there is no upper word-count or character limit.
- Count whitespace-separated tokens containing a Unicode letter or digit.
  Spaces, tabs, newlines, and Unicode spacing separators divide words.
  Punctuation-only or emoji-only tokens do not count, but do not invalidate
  otherwise sufficient text.
- There is no preset, matching, memorization, case, punctuation, or word-order
  requirement. Entered text is transient and never persisted. This is friction,
  not a password or trusted proof of manual typing.
- If disabled, Leave focus ends the session immediately without a text dialog.
- Start requires a usable normal launcher, Focus Harbor Home selection, safety
  acknowledgment, and at least one available selected app. The package set and
  exit choice are snapshotted.
- Changing setup or starting again cannot overwrite an active session's choice.
- The authoritative optional word-count check happens within the DataStore edit.
  UI button enablement is convenience, not authorization.
- When enabled, fewer than 10 words, Back, dismissal, and recreation do not
  end focus. When disabled, Back is also an immediate normal exit.
- An unavailable allowed app does not widen the allowlist. It is omitted and an
  explanation is shown. Phone and the exit challenge remain reachable.

## Upgrade behavior

Remove legacy persisted custom phrases during DataStore migration. Preserve an
already-active legacy session and its allowlist/timestamp, enabling the new
free-form word-count gate for that session. Idle legacy settings default the
optional gate off. Never overwrite an explicit new-format exit choice.

## Explicit non-goals

No whole-device kiosk, notification/Recents blocking, work/private-profile
management, app tamper protection, auto-DND, secret password, network access,
analytics, cloud sync, runtime LLM, account, or remote control.
Allowed apps can themselves open other apps or links. Android can change Home,
clear app data, force-stop, or uninstall the app. These are intentional recovery
routes, not normal in-app exits.

## Acceptance evidence

| ID | Criterion | Automated evidence / additional check |
| --- | --- | --- |
| F01 | Only allowed current-profile apps appear in focus | `FocusFlowTest.focusHomeOnlyShowsAllowlistedApps` |
| F02 | Any 10 or more words; 9 rejected, Unicode spacing supported | `ExitTextTest` |
| F03 | Active allowlist/exit choice cannot be overwritten | `FocusStoreTest.activeSessionCannotBeReconfiguredByStartingAgain` |
| F04 | Short text cannot end an opted-in session | `FocusStoreTest.shortTextNeverEndsAnOptedInSession`, `FocusFlowTest.anyTenOrMoreWordsEndAnOptedInSession` |
| F05 | Any valid text ends focus atomically and keeps next setup | `FocusStoreTest.anyTenOrMoreWordsEndFocusAndPreserveTheNextSessionChoice` |
| F06 | Recreation and Back cannot bypass an enabled challenge | `FocusFlowTest.backDismissalAndActivityRecreationDoNotEndFocus`; manually check force-stop/reboot on an emulator |
| F07 | Invalid/stale setup never starts focus | `FocusStoreTest.invalidSetupNeverPersistsAnActiveSession` |
| F08 | Concurrent starts cannot replace the winning session | `FocusStoreTest.simultaneousStartsCannotOverwriteTheWinningSession` |
| F09 | Storage failures cannot masquerade as success | `FocusStoreTest.storageErrorsPropagateInsteadOfPretendingFocusWasSaved`; visible VM error paths |
| F10 | Phone intent never places a call or asks for call permission | APK inspection and emulator dialer-open check; never call an emergency number |
| F11 | No dangerous/privileged/network permissions | `scripts/check.ps1` packaged-permission gate |
| F12 | User can restore their original launcher without app cooperation | `SetupFlowTest` restores the emulator's stock Home role in cleanup; uninstall recovery is an additional manual check |
| F13 | Opting out allows immediate exit without text | `FocusStoreTest.optingOutAllowsImmediateExitWithoutAnyText`, `FocusFlowTest.optingOutEndsFocusImmediatelyWithoutShowingATextChallenge`, `SetupFlowTest.userCanStartWithTheChallengeOffAndLeaveImmediately` |
| F14 | Upgrade preserves active intent and removes legacy phrases | `FocusStoreTest.upgradingAnActiveLegacySessionKeepsTheGateButDeletesTheOldText`, `FocusStoreTest.upgradingIdleLegacySettingsDefaultsTheOptionalGateOff` |
| F15 | Initial presets preserve old app selections and active sessions | `FocusPresetsAndTimingTest.presetInitializationPreservesLegacySelectionAndAnActiveSession` |
| F16 | Preset CRUD and app selections remain independent | `FocusPresetsAndTimingTest.switchingPresetsSavesIndependentSelections`, `SetupFlowTest.presetsCanBeCreatedRenamedAndDeletedFromSetup` |
| F17 | Intention appears on Home, survives recreation, and clears on exit | `FocusPresetsAndTimingTest.activePresetAndIntentionAreSnapshotsAndIntentionIsClearedOnExit`, `SetupFlowTest.presetsKeepSeparateAppsAndTheSessionShowsItsIntention` |
| F18 | Deadline expiry never exits or bypasses selected word friction | `FocusPresetsAndTimingTest.reachingTimeNeverEndsFocusOrBypassesTheWordChallenge`, `FocusFlowTest.timedCompletionPreservesIntentionAndExitPolicyAndCanKeepGoing` |
| F19 | Same-boot clock changes and reboot timing are handled explicitly | `FocusTimeTest` |
| F20 | Normal launcher choice persists and is immutable during focus | `FocusStoreTest.normalLauncherChoiceIsLockedDuringFocusAndSurvivesExit` |
| F21 | Idle Home forwards, while the app icon opens setup without changing Home role | `SetupFlowTest.idleHomeForwardsToTheSavedLauncherButTheAppIconStillOpensSetup` |
| F22 | Successful Leave/Finish returns to normal Home only after the selected exit check | `FocusFlowTest.anyTenOrMoreWordsEndAnOptedInSession`, `FocusFlowTest.timedFinishReturnsToNormalLauncherOnlyAfterTheWordChallenge` |
| F23 | Missing/legacy launcher settings offer recovery without restarting focus | `FocusFlowTest.unavailableNormalLauncherOffersRecoveryWithoutRestartingFocus`, `FocusFlowTest.legacySessionWithoutALauncherCanStillExitAndReachRecovery` |
| F24 | Repeated automatic handoffs stop; malformed storage does not become an idle default | `HomeHandoffGuardTest`, `FocusStoreTest.incompleteLauncherSettingsAreNotTreatedAsAnIdleDefault` |

OEM-specific behavior and Android 8/9 Home selection require device-matrix testing
before wider distribution. Do not describe emulator evidence as OnePlus testing.
