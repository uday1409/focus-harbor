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
| Setup | Select apps, choose the optional word challenge, select Home, accept the boundary | Successful atomic start |
| Focus | Only allowed installed apps shown; configuration hidden | Leave immediately if opted out; otherwise open the challenge |
| Exit challenge | Accept any fresh text with at least 10 words | Valid word count atomically ends focus; cancel stays focused |

Process death, configuration changes, reopening from the app icon, Home intents,
and reboot do not intentionally clear an active persisted session. After reboot,
the session is read when Android next opens the launcher; there is no boot service.
The elapsed clock is informational, uses wall time, and can change with clock edits.

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
- Start requires Home selection, safety acknowledgment, and at least one
  available selected app. The package set and exit choice are snapshotted.
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

OEM-specific behavior and Android 8/9 Home selection require device-matrix testing
before wider distribution. Do not describe emulator evidence as OnePlus testing.
