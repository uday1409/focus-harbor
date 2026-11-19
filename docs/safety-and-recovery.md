# Safety, limitations, and recovery

## What protects the device

Focus Harbor changes its own screen and app-private preferences only. It never
modifies firmware, bootloader, partitions, OS files, other apps' data, package
enabled states, networking, or a device-management policy. Becoming Home
requires Android's own user-controlled selection.

There is no root, device owner/admin, screen-content inspection, overlay,
lock-task pinning, usage-access monitor, VPN, or automatic DND. When the user
optionally enables **Block non-allowed apps & Recents** (`FocusAppGuardService`),
it uses an Android `AccessibilityService` strictly limited to foreground package
names (`canRetrieveWindowContent="false"`, `TYPE_WINDOW_STATE_CHANGED` only) and
never reads chat messages, passwords, or screen text.
The app does not request Internet, phone-call, contacts, location, storage, or
other dangerous `<uses-permission>` entries.

The phone button is always present, including during Strict Timebound sessions
and storage-error screens, and merely opens the installed dialer. Phone dialers,
telecom/in-call screens, emergency call UI, enabled keyboards, and the Android
Power menu are always exempt from App Guard blocking. Never test emergency
numbers.

No software can promise zero bugs on every device. The implementation is designed
to remain recoverable, not to make a universal "cannot corrupt a device"
certification. Do not use it as a child-safety, clinical, enterprise-management,
emergency, or security-control product.

## Modes and what each enforces

- **Home-only vs. Strict App Guard:** With Strict App Guard off, Focus Harbor
  limits the Home screen only (links, notifications, and Recents can still open
  other apps). With **Block non-allowed apps & Recents** turned on (after
  completing the one-time Android App Info + Accessibility toggle), opening a
  non-allowlisted app (such as a browser link from chat) or Recents immediately
  returns you to Focus Harbor until the session ends or a strict timer reaches
  `00:00`.
- **Open-ended vs. Strict Timebound (1–180 min):** Open-ended sessions let you
  tap **Leave focus** anytime (with an optional 10+ word challenge). Strict
  Timebound sessions cap duration at 180 minutes, clamp remaining time against
  clock jumps, and hide **Leave focus** until the countdown reaches `00:00`. At
  `00:00`, App Guard stops blocking immediately and **Finish** unlocks in one
  tap (or **Keep going** starts a new timer).

## Normal exit

- **Open-ended:** Tap **Leave focus**. If **Require 10+ words to leave focus**
  is off, focus ends in one tap; if on, write any 10+ words and tap **End
  focus**.
- **Strict Timebound:** Wait until the countdown reaches `00:00`, then tap
  **Finish** (1 tap, no word challenge).
- **Open Home switcher when leaving focus** is enabled by default so Android's
  Home settings open automatically right after focus ends, letting you switch
  back to your normal launcher in one tap.

## Recovery without cooperating with the app (Safe Mode & Settings)

1. **If Strict App Guard is off:** Open Android Settings from the notification
   shade → **Apps > Default apps > Home app** and select your usual launcher.
2. **Guaranteed hardware escape hatch (even during Strict Timebound + Strict App
   Guard):** Hold the **Power** button (or Power + Volume Up on OnePlus/Android
   13+) to open the system Power menu, **long-press Restart / Power off**, and tap
   **Reboot to safe mode**. Android Safe Mode automatically disables all
   third-party Accessibility services and custom Home launchers so you can
   switch Default Home or uninstall Focus Harbor freely, then restart normally.

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
