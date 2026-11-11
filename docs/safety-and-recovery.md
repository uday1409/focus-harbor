# Safety, limitations, and recovery

## What protects the device

Focus Harbor changes its own screen and app-private preferences only. It never
modifies firmware, bootloader, partitions, OS files, other apps' data, package
enabled states, networking, or a device-management policy. Becoming Home
requires Android's own user-controlled selection.

There is no root, device owner/admin, accessibility interception, overlay,
lock-task pinning, usage-access monitor, VPN, automatic DND, or blocking service.
The app does not request Internet, phone-call, contacts, location, storage, or
other dangerous permissions. AndroidX may contribute an app-specific
signature-only receiver permission; it is not a user-granted dangerous permission.

The phone button is always present, including storage-error screens, and merely
opens the installed dialer. It cannot guarantee that a dialer or emergency
service exists on a particular tablet, emulator, or carrier setup. The system
lock-screen emergency feature remains untouched. Never test emergency numbers.

No software can promise zero bugs on every device. The implementation is designed
to remain recoverable, not to make a universal "cannot corrupt a device"
certification. Do not use it as a child-safety, clinical, enterprise-management,
emergency, or security-control product.

## What it cannot enforce

Focus limits the Home screen only. Notifications, Recents, Quick Settings,
voice assistants, links, app-to-app navigation, Android settings, force-stop,
clearing storage, safe mode, and uninstall can bypass that screen. Selected apps
can open other apps. Work/private profiles and other users are not managed.
The UI explains this before each session can start.

The optional challenge accepts any text with at least 10 words. There is no
preset or matching requirement, and entered text is not saved. It introduces
intentional friction, not secrecy or adversarial lockout. Do not enter a password,
financial information, private personal data, or sensitive work information.
Keyboard/assistive input and paste are not cryptographically distinguishable from
manual typing.

## Normal exit

Before starting, optionally enable **Require 10+ words to leave focus**.
The option is off by default, remembered between sessions, and locked while active.
Tap **Leave focus**: if the option is off, the session ends immediately. If it is
on, write any text with at least 10 words and tap **End focus**. Exactly 10 and
longer texts are accepted; case, wording, and punctuation do not need to match
anything. Punctuation-only tokens do not count as words.
Focus setup then becomes editable again. Focus Harbor remains your Home app
until you change Android's Home selection.

On upgrade, an already-active old session retains the word-count challenge,
but its custom phrase is removed and no longer needs to be remembered or matched.

## Presets, intentions, and timed sessions

Presets store app selections and names only on this device. The Work preset
inherits your previous selection on upgrade. Preset changes cannot alter an
already-running session, and deleting a preset is confirmed before removal.

An optional intention is visible on Home and saved in app-private storage only
while the session is active, so reopening works. It is cleared on normal exit
and is never uploaded or logged. Do not enter sensitive information.

Timed sessions never auto-exit or launch another app. Finish and Keep going
appear when you are on or return to Home. Finish still follows your chosen
optional 10+ word challenge; "What do you need your phone for?" changes only the
question, not that rule. Keep going adds time or becomes open-ended explicitly.
No background reminders, exact alarms, notifications, breaks, DND changes, or
interference with system alarms is included.

## Recovery without cooperating with the app

1. Open Android Settings using the notification shade or Quick Settings.
2. Find **Apps > Default apps > Home app** (wording varies by manufacturer).
3. Choose your original launcher, such as the stock OnePlus launcher.
4. If desired, open **Apps > Focus Harbor** and uninstall it, or clear its app
   storage. Clearing storage removes only Focus Harbor's saved setup/session.

If your device requires its own safe-mode procedure, use its manufacturer's
instructions. Do not disable system apps, change firmware, use ADB package
removal against your stock launcher, or grant device-administrator access.
The app deliberately cannot hide its uninstall action or these recovery routes.

## Installation and signing

The delivered APK is debug-signed for personal evaluation, not Play Store
distribution. Installing it is a separate, manual user decision. Never bypass
an unsafe-content or device-management warning to install it.
Do not let the assistant change your phone's settings or install it without
device-specific approval.

Debug signing keys are machine-local. Builds on another machine may need the old
evaluation build uninstalled before installation, losing Focus Harbor data.
Select and protect a stable production signing key before any public release.
Keys and generated APKs do not belong in source control.
