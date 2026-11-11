---
name: build-apk
description: Build, inspect, checksum, and hand off a Focus Harbor Android APK, or diagnose Gradle and emulator failures without exposing signing keys or touching physical devices.
compatibility: JDK 17, Android SDK platform 36, Build Tools 35.0.0, and PowerShell 7 on Windows.
---

# APK delivery

1. Read the root [agent contract](../../../AGENTS.md) and
   [build guide](../../../docs/build.md). Check the worktree and do not revert
   unrelated changes. Verify `JAVA_HOME` and `ANDROID_HOME`; never commit paths.
2. From the repository root, run `.\scripts\check.ps1`. It runs unit tests and Android lint, builds the
   application/test APKs, checks the package/SDK/permission boundary and signature,
   and exports a debug-signed APK with SHA-256 into `dist`.
3. For an Android integration change, start a dedicated emulator, then run
   `.\scripts\check.ps1 -EmulatorSerial emulator-5586` with its actual serial.
   The script refuses physical-device serials and isolates Gradle/adb to that
   emulator. Never test an emergency number or install to a user's phone.
4. If a command fails, read its real error. Repair the underlying configuration
   or code; do not suppress lint, skip failing tests, fabricate an APK, or report
   a previous artifact as a successful new build.
5. Inspect the exported APK and checksum. Label it as a debug-signed evaluation
   build. Do not promise production signing or seamless updates across machines.
6. Publish only to the user's authorized personal repository. If authentication
   is unavailable, keep a local commit and state the publishing blocker plainly.
   Never obtain a token by inspecting browser state or another person's files.

Stop emulator helpers started for the task before returning control.
