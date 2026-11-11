#requires -Version 7.0
param(
    [ValidatePattern('^emulator-[0-9]+$')]
    [string]$EmulatorSerial
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$windowsHost = [OperatingSystem]::IsWindows()
$wrapper = Join-Path $root $(if ($windowsHost) { 'gradlew.bat' } else { 'gradlew' })
$previousSerial = $env:ANDROID_SERIAL

if (-not $env:JAVA_HOME -or -not $env:ANDROID_HOME) {
    throw 'Set JAVA_HOME (JDK 17) and ANDROID_HOME (Android SDK). See docs/build.md.'
}

$tools = [IO.Path]::Combine($env:ANDROID_HOME, 'build-tools', '35.0.0')
$aapt = Join-Path $tools $(if ($windowsHost) { 'aapt2.exe' } else { 'aapt2' })
$signer = Join-Path $tools $(if ($windowsHost) { 'apksigner.bat' } else { 'apksigner' })

Push-Location $root
try {
    if ($EmulatorSerial) {
        $adb = [IO.Path]::Combine(
            $env:ANDROID_HOME, 'platform-tools',
            $(if ($windowsHost) { 'adb.exe' } else { 'adb' })
        )
        $deviceState = & $adb -s $EmulatorSerial get-state
        if ($LASTEXITCODE -ne 0 -or $deviceState.Trim() -ne 'device') {
            throw "The dedicated emulator $EmulatorSerial is not online."
        }
        $emulated = & $adb -s $EmulatorSerial shell getprop ro.kernel.qemu
        if ($LASTEXITCODE -ne 0 -or $emulated.Trim() -ne '1') {
            throw 'Connected tests are permitted only on a verified emulator.'
        }
        $booted = & $adb -s $EmulatorSerial shell getprop sys.boot_completed
        if ($LASTEXITCODE -ne 0 -or $booted.Trim() -ne '1') {
            throw 'Wait for the dedicated emulator to finish booting before testing.'
        }
        $windows = & $adb -s $EmulatorSerial shell dumpsys window
        if ($LASTEXITCODE -ne 0) { throw 'Could not inspect emulator window health.' }
        if (($windows -join "`n") -match 'Application Not Responding') {
            throw 'The Android guest has an ANR dialog. Recover the emulator before testing; do not treat a guest startup failure as an app failure.'
        }
        $env:ANDROID_SERIAL = $EmulatorSerial
    }

    $tasks = @(
        ':app:testDebugUnitTest', ':app:lintDebug',
        ':app:assembleDebug', ':app:assembleDebugAndroidTest'
    )
    if ($EmulatorSerial) {
        $tasks += ':app:connectedDebugAndroidTest'
        $tasks += "-Pandroid.injected.device.serial=$EmulatorSerial"
    }
    & $wrapper --no-daemon --console=plain --continue @tasks
    if ($LASTEXITCODE -ne 0) { throw 'Android checks failed. No APK was exported.' }

    if (-not (Test-Path -LiteralPath $aapt) -or -not (Test-Path -LiteralPath $signer)) {
        throw 'Android Build Tools 35.0.0 are missing.'
    }
    $apk = [IO.Path]::Combine($root, 'app', 'build', 'outputs', 'apk', 'debug', 'app-debug.apk')
    $badging = & $aapt dump badging $apk
    if ($LASTEXITCODE -ne 0) { throw 'APK metadata inspection failed.' }
    $metadata = $badging -join "`n"
    if ($metadata -notmatch "package: name='dev\.focusharbor\.app'") {
        throw 'Unexpected APK application ID.'
    }
    if ($metadata -notmatch "sdkVersion:'26'" -or $metadata -notmatch "targetSdkVersion:'36'") {
        throw 'Unexpected APK minimum or target SDK.'
    }
    if ($metadata -notmatch "versionName='([^']+)'") { throw 'APK version is missing.' }
    $version = $Matches[1]

    $permissions = & $aapt dump permissions $apk
    if ($LASTEXITCODE -ne 0) { throw 'APK permission inspection failed.' }
    $uses = [regex]::Matches(
        ($permissions -join "`n"), "uses-permission[^:]*: name='([^']+)'"
    )
    foreach ($permission in $uses) {
        if ($permission.Groups[1].Value -ne 'dev.focusharbor.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION') {
            throw "Unexpected packaged permission: $($permission.Groups[1].Value)"
        }
    }
    & $signer verify --verbose $apk
    if ($LASTEXITCODE -ne 0) { throw 'APK signature verification failed.' }

    $dist = Join-Path $root 'dist'
    New-Item -ItemType Directory -Force -Path $dist | Out-Null
    $name = "focus-harbor-$version-debug.apk"
    $destination = Join-Path $dist $name
    Copy-Item -LiteralPath $apk -Destination $destination -Force
    $hash = (Get-FileHash -LiteralPath $destination -Algorithm SHA256).Hash.ToLowerInvariant()
    "$hash  $name" | Set-Content -LiteralPath "$destination.sha256" -Encoding ascii
    Write-Output "APK: $destination"
    Write-Output "SHA256: $hash"
} finally {
    $env:ANDROID_SERIAL = $previousSerial
    Pop-Location
}
