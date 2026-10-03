<#
    device_smoke.ps1 - the Windows twin of tools/device_smoke.sh.

    Opens every screen of the installed build once, saves a screenshot of each, then reads the
    log for crashes and inflation failures. It taps nothing, so it cannot freeze, uninstall or
    delete anything even on a build that holds root. The random-tap half (monkey) is a separate,
    opt-in switch with a warning, because a stray tap in this application can select packages
    and freeze them.

    How to run it without a terminal:
        1. Right-click this file, "Run with PowerShell".
        2. Answer the questions it asks (Enter accepts the shown default).

    How to run it from a terminal:
        powershell -ExecutionPolicy Bypass -File tools\device_smoke.ps1

    Options:
        -Out <folder>     where the screenshots go (default: %USERPROFILE%\freezeyou-smoke)
        -Monkey <count>   after the walk, send this many random taps, with a confirmation first
        -ListOnly         print what would be opened and exit, touching nothing
        -Serial <serial>  pick a device when several are attached (adb devices shows them)

    Exit codes: 0 clean, 1 findings in the log, 3 no adb or no device, 4 the screen list itself
    is broken (a duplicate, or a screen the manifest no longer has).
#>

param(
    [string]$Out = "$env:USERPROFILE\freezeyou-smoke",
    [int]$Monkey = 0,
    [switch]$ListOnly,
    [string]$Serial = ""
)

$ErrorActionPreference = "Stop"
$Package = "cf.playhi.freezeyou"
$Settle = 1.5

# Screens that only display something, so opening them is safe whatever privileges the build
# holds. Activities that need an intent to mean anything (a URI to freeze, a package to
# uninstall, a shortcut to create) are deliberately absent: without their intent they close at
# once and prove nothing.
$Screens = @(
    ".ui.AboutActivity",
    ".ui.BackupMainActivity",
    ".ui.BackupImportChooserActivity",
    ".ui.ManualModeActivity",
    ".ui.ScheduledTasksManageActivity",
    ".ui.ScheduledTasksAddActivity",
    ".ui.ScheduledTaskCommandsSyntaxActivity",
    ".ui.UserDefinedListsManageActivity",
    ".ui.FUFNotificationsManageActivity",
    ".ui.ShowLogcatActivity",
    ".ui.ShowSimpleDialogActivity",
    ".ui.ShortcutLauncherFolderActivity",
    ".ui.AutoDiagnosisActivity",
    ".ui.UriAutoAllowManageActivity",
    ".ui.FirstTimeSetupActivity",
    ".ui.SettingsActivity"
)

function Fail([string]$message, [int]$code) {
    Write-Host $message -ForegroundColor Red
    exit $code
}

# --- the list checks itself: no duplicates, and every screen is in the manifest ---
$duplicates = $Screens | Group-Object | Where-Object { $_.Count -gt 1 } | ForEach-Object { $_.Name }
if ($duplicates) {
    Fail ("The screen list has duplicates: " + ($duplicates -join ", ")) 4
}

$manifest = Join-Path $PSScriptRoot "..\app\src\main\AndroidManifest.xml"
if (Test-Path $manifest) {
    $manifestText = Get-Content -Raw $manifest
    $missing = $Screens | Where-Object { $manifestText -notmatch [regex]::Escape("android:name=""$_""") }
    if ($missing) {
        Fail ("These screens are not in the manifest any more: " + ($missing -join ", ")) 4
    }
} else {
    Write-Host "Note: the repository manifest was not found next to this script, so the" -ForegroundColor DarkYellow
    Write-Host "name check was skipped. That check only matters when editing the list." -ForegroundColor DarkYellow
}

if ($ListOnly) {
    Write-Host "Would open $($Screens.Count) screens of $Package:"
    $Screens | ForEach-Object { Write-Host "  $_" }
    Write-Host "Screenshots would go to $Out"
    if ($Monkey -gt 0) { Write-Host "Then monkey would send $Monkey random events." }
    exit 0
}

if (-not (Get-Command adb -ErrorAction SilentlyContinue)) {
    Fail "adb is not on PATH. Install the platform-tools and add them to PATH." 3
}

$adbArgs = @()
if ($Serial -ne "") { $adbArgs += @("-s", $Serial) }

$state = & adb @adbArgs get-state 2>$null
if ($state -ne "device") {
    Fail "No device. Connect the phone, allow USB debugging, and check 'adb devices'." 3
}

New-Item -ItemType Directory -Force -Path $Out | Out-Null
& adb @adbArgs logcat -c | Out-Null

Write-Host ""
Write-Host "Opening $($Screens.Count) screens of $Package. Screenshots: $Out"
Write-Host ""

foreach ($screen in $Screens) {
    $name = ($screen -split "\.")[-1]
    $target = "$Package/$screen"
    $previous = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    $start = & adb @adbArgs shell am start -n $target 2>&1
    $ErrorActionPreference = $previous
    if ($LASTEXITCODE -ne 0 -or ($start -join " ") -match "Error") {
        Write-Host ("  {0,-34} could not be started (not exported, or it needs an intent)" -f $name) -ForegroundColor DarkYellow
        continue
    }
    Start-Sleep -Seconds $Settle
    # Binary screenshot: redirection has to happen inside cmd, PowerShell would corrupt the bytes.
    $shot = Join-Path $Out "$name.png"
    & cmd /c "adb $($adbArgs -join ' ') exec-out screencap -p > `"$shot`""
    & adb @adbArgs shell input keyevent KEYCODE_BACK | Out-Null
    Start-Sleep -Milliseconds 500
    Write-Host ("  {0,-34} opened" -f $name)
}

if ($Monkey -gt 0) {
    Write-Host ""
    Write-Host "About to run monkey. Read this first:" -ForegroundColor Yellow
    Write-Host "  monkey taps at random inside the application, and this application can hold root" -ForegroundColor Yellow
    Write-Host "  or Shizuku. A stray tap can select applications in a list and freeze them, confirm" -ForegroundColor Yellow
    Write-Host "  the critical-packages dialog, or delete scheduled tasks. Revoke the elevated" -ForegroundColor Yellow
    Write-Host "  permission first, or use a spare device." -ForegroundColor Yellow
    $answer = Read-Host "Type yes to continue"
    if ($answer -ne "yes") {
        Write-Host "Monkey skipped."
    } else {
        & adb @adbArgs shell monkey -p $Package --pct-syskeys 0 --pct-appswitch 0 --throttle 300 -v $Monkey
        Write-Host "Monkey finished; the log below decides whether it was clean."
    }
}

Write-Host ""
Write-Host "Log since the walk, filtered:"
$log = & adb @adbArgs logcat -d 2>$null
$findings = $log | Select-String -Pattern "FATAL", "AndroidRuntime", "InflateException", "Resources.NotFoundException"
if ($findings) {
    $findings | ForEach-Object { Write-Host $_.Line -ForegroundColor Red }
    Write-Host ""
    Write-Host "Findings above. Screenshots are in $Out - they are for the eye, not the log:" -ForegroundColor Yellow
    Write-Host "the log cannot tell whether a screen looks right." -ForegroundColor Yellow
    Start-Process $Out
    exit 1
}

Write-Host "  nothing: no crash, no inflation failure, no missing resource." -ForegroundColor Green
Write-Host ""
Write-Host "Screenshots are in $Out. Looking through them is the part that catches a theme or" -ForegroundColor Green
Write-Host "a colour that went wrong without crashing anything." -ForegroundColor Green
Start-Process $Out
exit 0
