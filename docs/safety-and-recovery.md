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

The 10-word text is visible and stored in app-private plaintext. It introduces
intentional friction, not secrecy or adversarial lockout. Do not enter a password,
financial information, private personal data, or sensitive work information.
Keyboard/assistive input and paste are not cryptographically distinguishable from
manual typing.

## Normal exit

Tap **Leave focus**, re-enter the displayed ten-word text, and tap **End focus**.
Match case and punctuation. Extra spaces, tabs, and line breaks are normalized.
Focus setup becomes editable again. Focus Harbor remains your Home app until
you change Android's Home selection.

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
