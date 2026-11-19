# Focus mode specification

## Goal and boundary

Give a consenting Android user a calm focus space containing only selected apps,
supporting **Open-ended** sessions (with an optional 10-or-more-word exit
challenge), **Strict Timebound** sessions (1–180 minutes with manual exit
locked until `00:00`), and an optional **Strict App Guard** (package-only
`AccessibilityService` that blocks Recents and indirect non-allowlisted app
launches without reading screen text).
The app is inspired by Zen Space; it does not use OnePlus code, names, icons,
device-owner locks, or hidden OS APIs, and always preserves Phone access and
Android Safe Mode recovery.

Supported: Android 8.0/API 26 and later, current Android profile only.
Compile/target API: 36.

## States

| State | Behavior | Transition |
| --- | --- | --- |
| Loading | No guessed session; phone and recovery guidance available | Read persisted preferences |
| Read error | Explicit failure; no setup, app launch, or new session | Retry storage or recover through Android |
| Setup | 1-tap preset & duration chips, optional Strict App Guard, optional intention/word challenge, one-time safety note, and Home role | Successful atomic start |
| Focus (Open-ended) | Only allowed installed apps shown; Back stays on Focus Home | Leave immediately if opted out; otherwise open the 10+ word challenge |
| Focus (Strict Timebound) | Only allowed installed apps shown; manual exit locked (`strict-timer-locked`) while `remainingMillis > 0` | Countdown reaches `00:00` |
| Exit challenge | Open-ended only: accept any fresh text with at least 10 words | Valid word count atomically ends focus; cancel or Back stays focused |
| Time reached | Keep focus active, stop App Guard blocking immediately, and show 1-tap Finish / Keep going on Home | Finish ends focus in 1 tap; Keep going sets a new strict deadline or open-ended mode |

Process death, configuration changes, reopening from the app icon, Home intents,
and reboot do not intentionally clear an active persisted session. After reboot,
the session is read when Android next opens the launcher; there is no boot service.
The elapsed clock is informational and uses wall time. Timed deadlines use
monotonic elapsed time on the same boot, with a saved wall-clock deadline after
reboot or when the platform boot counter is unavailable.

## Entering and switching Home cleanly

- Focus Harbor never proxies or forwards idle `CATEGORY_HOME` intents to another
  launcher. Switching default launchers uses Android's native Home chooser only.
- When Focus Harbor is not yet the default Home app, tapping **Set Home & enter
  focus** opens Android's Home role chooser (API 29+) or Home settings (API 26-28)
  and starts the session automatically once Focus Harbor holds the Home role.
- Optionally enable **Open Home switcher when leaving focus** (enabled by
  default to minimize taps, remembered between sessions, and locked while
  active). When enabled and Focus Harbor still holds the Home role, a successful
  exit (`Leave focus` or timed `Finish`) opens `Settings.ACTION_HOME_SETTINGS`
  once so the user can restore their usual launcher in one tap. Returning from
  Settings never re-triggers it.
- When focus is off and Focus Harbor still holds the Home role (for example,
  right after ending a session), the top card offers **Switch to normal Home**
  (`Settings.ACTION_HOME_SETTINGS`) so the user can restore their usual launcher
  in one tap without background forwarding loops.
- The safety acknowledgment is accepted once and persisted in DataStore so
  subsequent sessions do not require repetitive re-checking; **Recovery & safety**
  remains available in the bottom bar at all times.

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
- Default to open-ended. Offer 1-tap `Open-ended`, `15m`, `25m`, `45m`, `60m`,
  and `Custom…` (1–180 minutes) chips, plus 1-tap preset chips. Remember the
  last start-duration choice independently of presets.
- **Strict Timebound mode (1–180 minutes):** While `remainingMillis > 0`,
  manual exit is disabled in both the UI (`strict-timer-locked`) and inside
  `FocusStore.endSession` (`STRICT_TIMER_ACTIVE`). `remainingMillis` is
  monotonically clamped to `0..(durationMinutes * 60_000)` so wall-clock jumps
  never extend a strict timer.
- Expiry never auto-exits or launches another app, but immediately stops
  `FocusAppGuardService` blocking and unlocks 1-tap **Finish** or **Keep going**
  on Home (the 10+ word challenge applies only to open-ended sessions).
- Show Finish / Keep going on Home when due, including after process death or
  reboot. No alarm, background popup, or notification permission is added.
- Keep going adds a newly selected strict duration (1–180 min) from the time of
  the explicit action, or converts the ongoing session to open-ended. Preserve
  apps, intention, guard setting, and the original start time.
- **Strict App Guard (optional):** Before starting, the user can enable **Block
  non-allowed apps & Recents** (`strictAppGuard`). If `FocusAppGuardService` is
  not yet enabled in Android Accessibility Settings, Setup opens a guided 2-step
  dialog with direct buttons to **1. Open App Info** (for Android 13+ sideloaded
  `Allow restricted settings` and `Unrestricted` battery) and **2. Open
  Accessibility Settings**. During an active guarded session (while `remaining`
  is `null` or `> 0`), `FocusAppGuardService` checks `TYPE_WINDOW_STATE_CHANGED`
  package names only (`canRetrieveWindowContent="false"`) and bounces
  non-allowlisted packages and Recents back to Focus Harbor. Phone dialers,
  emergency/in-call UI, enabled IME keyboards, and non-Recents SystemUI (Power
  menu / Safe Mode) are always exempt.
- The exit dialog asks "What do you need your phone for?" for open-ended
  sessions with the 10+ word challenge enabled.
- Temporary breaks are not part of this release. Alarms, DND, and Safe Mode
  recovery remain entirely under Android/user control.

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
- Start requires Focus Harbor Home selection, one-time safety acknowledgment,
  and at least one available selected app. The package set and exit choice are
  snapshotted.
- Changing setup or starting again cannot overwrite an active session's choice.
- The authoritative optional word-count check happens within the DataStore edit.
  UI button enablement is convenience, not authorization.
- System Back on Focus Home never ends focus (whether the word challenge is
  enabled or disabled) and never finishes the Home activity; when the exit
  dialog is open, Back closes only the dialog.
- An unavailable allowed app does not widen the allowlist. It is omitted and an
  explanation is shown. Phone and the exit challenge remain reachable.

## Upgrade behavior

Remove legacy persisted custom phrases and obsolete v1.3 normal-launcher keys
during DataStore migration. Preserve an already-active legacy session and its
allowlist/timestamp, enabling the new free-form word-count gate for that session.
Idle legacy settings default the optional gate off. Never overwrite an explicit
new-format exit choice.

## Explicit non-goals

No device-owner/MDM kiosk, notification-text reading, screen-content inspection,
work/private-profile management, app tamper protection, auto-DND, secret
password, network access, analytics, cloud sync, runtime LLM, account, or remote
control. Android Safe Mode (holding Power → long-pressing Restart) intentionally
disables third-party launchers and Accessibility services as a guaranteed
hardware escape hatch.

## Acceptance evidence

| ID | Criterion | Automated evidence / additional check |
| --- | --- | --- |
| F01 | Only allowed current-profile apps appear in focus | `FocusFlowTest.focusHomeOnlyShowsAllowlistedApps` |
| F02 | Any 10 or more words; 9 rejected, Unicode spacing supported | `ExitTextTest` |
| F03 | Active allowlist/exit choice cannot be overwritten | `FocusStoreTest.activeSessionCannotBeReconfiguredByStartingAgain` |
| F04 | Short text cannot end an opted-in open-ended session | `FocusStoreTest.shortTextNeverEndsAnOptedInSession`, `FocusFlowTest.anyTenOrMoreWordsEndAnOptedInSession` |
| F05 | Any valid text ends open-ended focus atomically and keeps next setup | `FocusStoreTest.anyTenOrMoreWordsEndFocusAndPreserveTheNextSessionChoice` |
| F06 | Recreation and Back cannot bypass an enabled challenge | `FocusFlowTest.backDismissalAndActivityRecreationDoNotEndFocus`; manually check force-stop/reboot on an emulator |
| F07 | Invalid/stale setup or >180m duration never starts focus | `FocusStoreTest.invalidSetupNeverPersistsAnActiveSession`, `FocusPresetsAndTimingTest.invalidPlansNeverStartOrMutateASession` |
| F08 | Concurrent starts cannot replace the winning session | `FocusStoreTest.simultaneousStartsCannotOverwriteTheWinningSession` |
| F09 | Storage failures cannot masquerade as success | `FocusStoreTest.storageErrorsPropagateInsteadOfPretendingFocusWasSaved`; visible VM error paths |
| F10 | Phone intent never places a call or asks for call permission | APK inspection and emulator dialer-open check; never call an emergency number |
| F11 | No dangerous/privileged/network `<uses-permission>` entries | `scripts/check.ps1` packaged-permission gate |
| F12 | User can restore their original launcher without app cooperation | `SetupFlowTest` restores the emulator's stock Home role in cleanup; Safe Mode / uninstall recovery is documented |
| F13 | Opting out allows immediate exit without text and one-tap re-entry | `FocusStoreTest.optingOutAllowsImmediateExitWithoutAnyText`, `FocusFlowTest.optingOutEndsFocusImmediatelyWithoutShowingATextChallenge`, `SetupFlowTest.userCanStartWithTheChallengeOffAndReenterWithoutRecheckingSafety` |
| F14 | Upgrade preserves active intent and removes legacy phrases/launcher keys | `FocusStoreTest.upgradingAnActiveLegacySessionKeepsTheGateButDeletesTheOldText`, `FocusStoreTest.upgradingIdleLegacySettingsDefaultsTheOptionalGateOff`, `FocusStoreTest.upgradingV13SettingsRemovesObsoleteLauncherKeysWhilePreservingSession` |
| F15 | Initial presets preserve old app selections and active sessions | `FocusPresetsAndTimingTest.presetInitializationPreservesLegacySelectionAndAnActiveSession` |
| F16 | 1-tap preset CRUD and app selections remain independent | `FocusPresetsAndTimingTest.switchingPresetsSavesIndependentSelections`, `SetupFlowTest.presetsCanBeCreatedRenamedAndDeletedFromSetup` |
| F17 | Intention appears on Home, survives recreation, and clears on exit | `FocusPresetsAndTimingTest.activePresetAndIntentionAreSnapshotsAndIntentionIsClearedOnExit`, `SetupFlowTest.presetsAndDurationChipsSwitchInOneTapAndStartStrictTimeboundFocus` |
| F18 | Strict Timebound blocks early exit until `00:00`, then unlocks 1-tap Finish or Keep going | `FocusPresetsAndTimingTest.strictTimedSessionBlocksEarlyExitThenFinishesInOneTapOrKeepsGoing`, `FocusFlowTest.strictTimedSessionHidesManualExitWhileCountingDown`, `FocusFlowTest.timedCompletionUnlocksAndCanKeepGoingIntoANewStrictCountdown`, `FocusFlowTest.timedFinishEndsInOneTapWithoutWordChallengeAndClearsIntention` |
| F19 | Same-boot clock changes, reboots, and backward clock jumps clamp safely | `FocusTimeTest` |
| F20 | Back never ends an active session even when the word challenge is off | `FocusFlowTest.backPressNeverEndsFocusEvenWhenChallengeIsOff` |
| F21 | One-time safety acknowledgment persists across sessions and locks while active | `FocusStoreTest.safetyAcknowledgmentPersistsAcrossSessionsAndIsLockedWhileActive`, `SetupFlowTest.userCanStartWithTheChallengeOffAndReenterWithoutRecheckingSafety` |
| F22 | Auto-open Home switcher defaults on, fires once after valid exit, and remembers choice | `FocusStoreTest.openHomeOnExitDefaultsTrueSnapshotsLocksWhileActiveAndRemembersPreference`, `SetupFlowTest.openHomeSwitcherOnExitDefaultsOnAndLaunchesAndroidHomeSettingsOnce` |
| F23 | Package-only App Guard blocks non-allowlisted apps & Recents while exempting Phone/IME/Emergency/SystemUI and stopping at `00:00` | `AppGuardPolicyTest`, `FocusStoreTest.strictAppGuardSnapshotsLocksWhileActiveAndRemembersPreference`, `SetupFlowTest.enabledStrictAppGuardBouncesNonAllowlistedAppsBackToFocusHome` |
| F24 | Toggling App Guard without Accessibility opens guided 2-step App Info + Accessibility setup | `SetupFlowTest.togglingAppGuardWithoutAccessibilityOpensGuidedTwoStepDialog` |

OEM-specific behavior and Android 8/9 Home selection require device-matrix testing
before wider distribution. Do not describe emulator evidence as OnePlus testing.
