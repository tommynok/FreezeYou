# device_smoke.ps1 - the Windows one. If this first line does not say "device_smoke.ps1",
# you were given the Linux twin (tools/device_smoke.sh, it starts with "#!/usr/bin/env bash");
# PowerShell cannot run that one and stops with a screenful of parse errors.
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
        powershell -ExecutionPolicy Bypass -File C:\device_smoke.ps1

    Options:
        -Out <folder>     where the screenshots go (default: %USERPROFILE%\freezeyou-smoke)
        -Monkey <count>   after the walk, send this many random taps, with a confirmation first
        -ListOnly         print what would be opened and exit, touching nothing
        -NoSu             do not fall back to root for the screens plain adb cannot open
        -Serial <serial>  pick a device when several are attached (adb devices shows them)

    On root: most screens in this application are declared not exported, and a plain
    "adb shell am start" is refused for those - on the first run of this script only 3 of the 16
    opened, while the log looked clean. When root is present the script retries those screens
    through "su -c", which the system does allow (root is exempt from the export check). Root is
    used only to start a screen; nothing is tapped and nothing is changed. If there is no root,
    the script says which screens stayed closed instead of pretending the walk was complete.

    Exit codes: 0 clean, 1 findings in the log, 2 the walk did not cover every screen,
    3 no adb or no device, 4 the screen list itself is broken.
#>

param(
    [string]$Out = "$env:USERPROFILE\freezeyou-smoke",
    [int]$Monkey = 0,
    [switch]$ListOnly,
    [switch]$NoSu,
    [string]$Serial = ""
)

$ErrorActionPreference = "Stop"
$Package = "cf.playhi.freezeyou"
$Settle = 1.5

Write-Host "device_smoke.ps1 (Windows) - opens screens, taps nothing. Twin: tools/device_smoke.sh" `
    -ForegroundColor DarkGray

# Screens that only display something, so opening them is safe whatever privileges the build
# holds. Activities that need an intent to mean anything (a URI to freeze, a package to
# uninstall, a shortcut to create) are deliberately absent: without their intent they close at
# once and prove nothing. Only ScheduledTasksManageActivity, ShortcutLauncherFolderActivity and
# SettingsActivity are exported; the rest need root, see the note above.
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
if (-not (Test-Path $manifest)) {
    $manifest = Join-Path $PSScriptRoot "app\src\main\AndroidManifest.xml"
}
if (Test-Path $manifest) {
    $manifestText = Get-Content -Raw $manifest
    $missing = $Screens | Where-Object { $manifestText -notmatch [regex]::Escape("android:name=""$_""") }
    if ($missing) {
        Fail ("These screens are not in the manifest any more: " + ($missing -join ", ")) 4
    }
} else {
    Write-Host "Note: the repository manifest is not next to this script, so the name check was" -ForegroundColor DarkYellow
    Write-Host "skipped. Copy the whole tools\ folder out of the repository to keep it." -ForegroundColor DarkYellow
}

if ($ListOnly) {
    Write-Host "Would open $($Screens.Count) screens of ${Package}:"
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

$previousPreference = $ErrorActionPreference
$ErrorActionPreference = "Continue"
$state = & adb @adbArgs get-state 2>$null
$ErrorActionPreference = $previousPreference
if ($state -ne "device") {
    Fail "No device. Connect the phone, allow USB debugging, and check 'adb devices'." 3
}

New-Item -ItemType Directory -Force -Path $Out | Out-Null
& adb @adbArgs logcat -c | Out-Null

# Runs one adb command and returns its output with the exit code, without letting a failing
# native command stop the script.
function Invoke-Adb([string[]]$AdbCommand) {
    $previous = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    $out = (& adb @adbArgs @AdbCommand 2>&1 | Out-String).Trim()
    $code = $LASTEXITCODE
    $ErrorActionPreference = $previous
    return [pscustomobject]@{ Output = $out; Code = $code }
}

# am start prints its refusal on stderr and still exits 0 often enough, so the text is checked
# as well as the code.
function Start-Screen([string]$target, [bool]$withRoot) {
    $command = if ($withRoot) { "su -c 'am start -n $target'" } else { "am start -n $target" }
    $result = Invoke-Adb -AdbCommand @("shell", $command)
    $bad = ($result.Code -ne 0) -or ($result.Output -match "Error|Exception|not exported|Permission Denial|not found")
    $firstLine = ($result.Output -split "`r?`n" | Where-Object { $_.Trim() -ne "" } | Select-Object -First 1)
    return [pscustomobject]@{ Ok = (-not $bad); Message = $firstLine }
}

# Which screen is actually on top. An activity can start and close at once; without this check
# the screenshot would silently show the screen underneath and the walk would count it as covered.
function Get-Foreground() {
    $result = Invoke-Adb -AdbCommand @("shell", "dumpsys activity activities")
    $match = [regex]::Match($result.Output, "ResumedActivity[:=]\s*ActivityRecord\{[^}]*?([A-Za-z0-9_.]+/[^ }\r\n]+)")
    if ($match.Success) { return $match.Groups[1].Value }
    return ""
}

# Root, if the phone offers it: the only way to open the screens that are not exported.
$suAvailable = $false
if (-not $NoSu) {
    $probe = (Invoke-Adb -AdbCommand @("shell", "command -v su")).Output
    $suAvailable = ($probe.Trim() -ne "") -and ($probe -notmatch "not found|No such|inaccessible")
}
if ($suAvailable) {
    Write-Host "Root is available on the device; it will be used only to open the screens that" -ForegroundColor DarkYellow
    Write-Host "plain adb refuses. If the phone asks whether to allow root for the shell, allow it." -ForegroundColor DarkYellow
}

Write-Host ""
Write-Host "Opening $($Screens.Count) screens of $Package. Screenshots: $Out"
Write-Host ""

# The main screen starts the process, so the first screen of the walk is not paid for by
# an application that is still starting up.
Start-Screen "$Package/.Main" $false | Out-Null
Start-Sleep -Seconds 1
& adb @adbArgs shell input keyevent KEYCODE_BACK | Out-Null

$notOpened = New-Object System.Collections.ArrayList
$opened = 0

foreach ($screen in $Screens) {
    $name = ($screen -split "\.")[-1]
    $target = "$Package/$screen"
    $route = "plain adb"

    $result = Start-Screen $target $false
    if (-not $result.Ok -and $suAvailable) {
        $result = Start-Screen $target $true
        $route = "root (su)"
    }

    if (-not $result.Ok) {
        $reason = if ($result.Message) { $result.Message } else { "no reason given" }
        if ($reason -match "not exported") {
            $reason = if ($suAvailable) {
                "not exported, and root did not open it either"
            } else {
                "not exported: plain adb cannot start it, and no root was found"
            }
        }
        Write-Host ("  {0,-36} not opened: {1}" -f $name, $reason) -ForegroundColor DarkYellow
        [void]$notOpened.Add([pscustomobject]@{ Screen = $name; Reason = $reason })
        continue
    }

    Start-Sleep -Seconds $Settle
    $shot = Join-Path $Out "$name.png"
    # Binary screenshot: redirection has to happen inside cmd, PowerShell would corrupt the bytes.
    & cmd /c "adb $($adbArgs -join ' ') exec-out screencap -p > `"$shot`""

    $foreground = Get-Foreground
    $expected = $screen.TrimStart(".")
    $onScreen = ($foreground -ne "") -and (($foreground -split "/")[-1].EndsWith($expected))
    & adb @adbArgs shell input keyevent KEYCODE_BACK | Out-Null
    Start-Sleep -Milliseconds 500

    if ($onScreen) {
        $opened++
        Write-Host ("  {0,-36} opened ({1})" -f $name, $route)
    } else {
        $what = if ($foreground) { $foreground } else { "unknown" }
        $reason = "opened, then closed at once (on screen: $what)"
        Write-Host ("  {0,-36} {1}" -f $name, $reason) -ForegroundColor DarkYellow
        [void]$notOpened.Add([pscustomobject]@{ Screen = $name; Reason = $reason })
    }
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
$hasFindings = [bool]$findings
if ($hasFindings) {
    $findings | ForEach-Object { Write-Host $_.Line -ForegroundColor Red }
    Write-Host ""
    Write-Host "Findings above." -ForegroundColor Yellow
} else {
    Write-Host "  nothing: no crash, no inflation failure, no missing resource." -ForegroundColor Green
    Write-Host ""
}

Write-Host ("Covered {0} screen(s) of {1}." -f $opened, $Screens.Count)
if ($notOpened.Count -gt 0) {
    Write-Host "Not covered - the log above says nothing about these:" -ForegroundColor Yellow
    foreach ($item in $notOpened) {
        Write-Host ("  {0,-36} {1}" -f $item.Screen, $item.Reason) -ForegroundColor Yellow
    }
    Write-Host "Open them by hand, or run again with root allowed. Screenshots are in $Out." -ForegroundColor Yellow
} else {
    Write-Host "Screenshots are in $Out. Looking through them is the part that catches a theme or" -ForegroundColor Green
    Write-Host "a colour that went wrong without crashing anything." -ForegroundColor Green
}
try { Start-Process $Out } catch { Write-Host "Open the folder by hand: $Out" -ForegroundColor DarkYellow }

if ($hasFindings) { exit 1 }
if ($notOpened.Count -gt 0) { exit 2 }
exit 0
