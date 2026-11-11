# Focus mode specification

## Goal and boundary

Give a consenting Android user a calm Home screen containing only selected apps,
with an intentional 10-word re-entry challenge for normal in-app exit.
The app is inspired by the idea of a distraction-free space; it does not use
OnePlus code, names, icons, or privileged OS features.

Supported: Android 8.0/API 26 and later, current Android profile only.
Compile/target API: 36. This is a Home-screen filter, not an app firewall.

## States

| State | Behavior | Transition |
| --- | --- | --- |
| Loading | No guessed session; phone and recovery guidance available | Read persisted preferences |
| Read error | Explicit failure; no setup, app launch, or new session | Retry storage or recover through Android |
| Setup | Select apps, enter text, select Home, accept the boundary | Successful atomic start |
| Focus | Only allowed installed apps shown; configuration hidden | Open exit challenge |
| Exit challenge | Display stored text and accept a fresh re-entry | Matching transaction ends focus; cancel stays focused |

Process death, configuration changes, reopening from the app icon, Home intents,
and reboot do not intentionally clear an active persisted session. After reboot,
the session is read when Android next opens the launcher; there is no boot service.
The elapsed clock is informational, uses wall time, and can change with clock edits.

## Configuration and exit

- Exactly 10 whitespace-separated tokens, each with a Unicode letter or digit.
  Punctuation-only tokens are invalid. Maximum raw input: 300 UTF-16 code units.
- Case, punctuation, and word order must match. Leading/trailing whitespace,
  repeated spaces, Unicode spacing separators, tabs, and line breaks normalize
  to a single space.
- The text is displayed during exit and stored locally in plaintext inside
  app-private DataStore. It is a friction exercise, not a secret or password.
  Assistive input/IME or paste cannot be made into a trusted human-typing proof.
- Start requires Home selection, safety acknowledgment, a valid text, and at
  least one available selected app. The chosen package set is snapshotted.
- Changing setup or starting again cannot overwrite an active session.
- The authoritative exit comparison happens within the DataStore edit.
  UI button enablement is convenience, not authorization.
- Wrong input, Back, dialog dismissal, and recreation do not end focus.
- An unavailable allowed app does not widen the allowlist. It is omitted and an
  explanation is shown. Phone and the exit challenge remain reachable.

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
| F02 | Exactly ten words; whitespace normalized, case/punctuation exact | `ExitPhraseTest` |
| F03 | Active allowlist/text cannot be overwritten | `FocusStoreTest.activeSessionCannotBeReconfiguredByStartingAgain` |
| F04 | Incorrect text never ends a session | `FocusStoreTest.incorrectTextNeverEndsAnActiveSession`, `FocusFlowTest.onlyMatchingTenWordTextEndsFocus` |
| F05 | Correct text atomically ends focus and keeps next setup | `FocusStoreTest.matchingTextEndsFocusButPreservesTheNextSessionConfiguration` |
| F06 | Recreation and Back cannot clear focus | `FocusFlowTest.backDismissalAndActivityRecreationDoNotEndFocus`; manually check force-stop/reboot on an emulator |
| F07 | Invalid/stale setup never starts focus | `FocusStoreTest.invalidSetupNeverPersistsAnActiveSession` |
| F08 | Concurrent starts cannot replace the winning session | `FocusStoreTest.simultaneousStartsCannotOverwriteTheWinningSession` |
| F09 | Storage failures cannot masquerade as success | `FocusStoreTest.storageErrorsPropagateInsteadOfPretendingFocusWasSaved`; visible VM error paths |
| F10 | Phone intent never places a call or asks for call permission | APK inspection and emulator dialer-open check; never call an emergency number |
| F11 | No dangerous/privileged/network permissions | `scripts/check.ps1` packaged-permission gate |
| F12 | User can restore their original launcher without app cooperation | `SetupFlowTest` restores the emulator's stock Home role in cleanup; uninstall recovery is an additional manual check |

OEM-specific behavior and Android 8/9 Home selection require device-matrix testing
before wider distribution. Do not describe emulator evidence as OnePlus testing.
