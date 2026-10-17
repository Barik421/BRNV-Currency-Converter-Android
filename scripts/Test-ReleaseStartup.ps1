param(
    [string]$Serial = 'emulator-5554',
    [string]$Adb = "$env:LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe"
)
$ErrorActionPreference = 'Stop'
if ($Serial -notmatch '^emulator-[0-9]+$') { throw 'Use an isolated emulator for this smoke test.' }
$root = Split-Path $PSScriptRoot -Parent
$package = 'com.brnv.currencyconverter'
& $Adb -s $Serial install -r (Join-Path $root 'app/build/outputs/apk/release/app-release.apk')
if ($LASTEXITCODE -ne 0) { throw 'Install failed. A debug build with a different signing key may already be installed.' }
& $Adb -s $Serial logcat -b crash -c
& $Adb -s $Serial shell am force-stop $package
& $Adb -s $Serial shell am start -W -n "$package/.MainActivity"
if ($LASTEXITCODE -ne 0) { throw 'Release activity did not launch.' }
Start-Sleep -Seconds 10
$crashes = & $Adb -s $Serial logcat -b crash -d
if ($crashes -match "Process: $([regex]::Escape($package))") { throw 'Release crashed after launch. Inspect adb logcat -b crash.' }
$activities = & $Adb -s $Serial shell dumpsys activity activities
if (!(($activities | Select-String 'ResumedActivity') -match [regex]::Escape($package))) {
    throw 'Release is not the resumed activity after launch.'
}
Write-Output 'PASS: signed release remains open after cold start. Functional and offline checks are still required.'
