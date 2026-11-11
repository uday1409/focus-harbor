# Build and verify

## Prerequisites

- JDK 17 (`JAVA_HOME`).
- Android SDK (`ANDROID_HOME`), platform 36, Build Tools 35.0.0, platform-tools.
- PowerShell 7 for the cross-platform artifact/check script.
- For UI acceptance: a dedicated Android API 36 x86_64 emulator with at least two
  launchable system apps. Hardware virtualization is recommended.

Use Android Studio's SDK Manager to install the SDK components or your supported
Android command-line tools. The repository does not download tools into source,
change machine-wide environment variables, or commit a local SDK path.
Dependencies are fetched by the checksum-pinned Gradle wrapper.

The original Windows build uses an isolated toolchain in the sibling
`.focus-harbor-tools` directory. This is a local convenience, not part of the repo:

```powershell
$tools = Join-Path (Split-Path (Get-Location) -Parent) '.focus-harbor-tools'
$env:JAVA_HOME = Join-Path $tools 'jdk-17.0.21+9'
$env:ANDROID_HOME = Join-Path $tools 'android-sdk'
.\scripts\check.ps1
```

For another machine, point those variables at your own JDK 17 and Android SDK.

## One-command checks and APK export

```powershell
.\scripts\check.ps1
```

This runs unit tests and Android lint (correctness/style warnings fail), compiles both APKs,
inspects the application's package ID, SDK range and packaged permissions,
verifies its signature, and exports:

```text
dist/focus-harbor-1.1.0-debug.apk
dist/focus-harbor-1.1.0-debug.apk.sha256
```

These are ignored generated files. The APK is debug-signed, debuggable, and
intended for personal evaluation. A production signing key/release policy has
not been configured.

The SDK/dependency-freshness checks (`AndroidGradlePluginVersion`,
`GradleDependency`, `NewerVersionAvailable`, `OldTargetApi`) remain enabled as
informational advice. This preview intentionally targets tested API 36; a runner
having SDK 37 installed must not force an untested compatibility change just to
pass lint. Retargeting requires compatibility review and testing. Weekly
Dependabot updates manage dependency upgrades; no correctness/security lint check
is disabled and there is no lint baseline.

The actual Gradle command, also usable on Linux/macOS, is:

```sh
./gradlew --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
```

Windows uses `.\gradlew.bat` instead. Test reports are under
`app/build/reports/tests/testDebugUnitTest`; lint writes
`app/build/reports/lint-results-debug.html`.

## Dedicated emulator acceptance

Do not run a connected test against an unspecified device. Do not attach this
task to the user's real phone. Start an isolated AVD, note its serial from
`adb devices`, then:

```powershell
.\scripts\check.ps1 -EmulatorSerial emulator-5586
```

The script validates that the serial identifies an emulator, scopes
`ANDROID_SERIAL`, and restores the previous environment value afterward.
The guest must be fully booted and unlocked. A guest ANR dialog causes an explicit
preflight failure instead of misleading app-test results; recover that emulator
or use a healthy AVD before proceeding.
The instrumented tests change this app's preferences on that emulator. The setup
acceptance test temporarily grants the emulator's Home role and restores its
original launcher in cleanup; it refuses physical hardware. Tests never place
calls.

Acceptance tests cover both optional exit choices, setup/start, Home-role recovery,
allowlist visibility, the 9/10/11-word boundary, Back/dismissal, and recreation. Reports are under
`app/build/reports/androidTests/connected/debug`.
Use an emulator for additional force-stop/reboot/Home-role recovery checks.
Do not claim physical-device or OEM coverage from these tests.

## GitHub

The built evaluation APK and its checksum are available in the
[private preview release](https://github.com/uday1409/focus-harbor/releases/tag/v1.1.0-preview).
Sign in to the repository owner's GitHub account to download private assets.

The workflow builds an APK/checksum artifact and runs API 36 emulator tests
on pushes to `main` and manual dispatch. Pull requests run the JVM/lint/build
job without the heavier emulator job. CI uses immutable action commit pins,
read-only repository permissions, and no signing secret.
Headless CI uses SwiftShader with Vulkan disabled to avoid host graphics
interoperability failures. This changes only the test emulator, not the app or
a user's device. Failed acceptance runs retain Android window state and system
events in the emulator-report artifact without hiding the original test failure.
The emulator job pins Ubuntu 24.04 and uses the API 36 Google APIs image with
four virtual cores and 4 GB RAM so cold-boot system services have sufficient
resources. The app itself does not depend on Google Play services.

After authenticating GitHub CLI to the intended personal account, publishing a
new private repository from a local committed checkout is explicit:

```powershell
gh auth login --hostname github.com --git-protocol https
gh api user --jq .login
gh repo create uday1409/focus-harbor --private --source . --remote origin --push
```

Verify the account before creating the repository. Do not reuse this command if
that repository or an unrelated `origin` already exists. The APK is available in
the successful workflow's `focus-harbor-debug-apk` artifact; generated APKs and
keystores are intentionally not committed.
