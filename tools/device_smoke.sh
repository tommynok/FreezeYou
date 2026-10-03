#!/usr/bin/env bash
#
# Windows: this is the Linux/macOS twin. On Windows run tools/device_smoke.ps1 instead -
# PowerShell cannot read this file and stops with a screenful of parse errors if fed it.
#
# Opens every screen of the installed build once, takes a screenshot of each, and then looks at
# the log for crashes and inflation failures. It taps nothing: opening a screen cannot freeze,
# uninstall or delete anything, which is what makes this the safe half of a smoke test. The
# random-tap half (monkey) lives in a separate mode below, and it is the one that can misbehave
# on a build that holds root, so it is opt-in and refuses to run without --monkey.
#
# Most screens here are declared not exported, and a plain "adb shell am start" is refused for
# those: the first run opened 3 of 16 while the log still looked clean. When root is present the
# script retries such a screen through "su -c", which the system allows (root is exempt from the
# export check). Root is used only to start a screen. Without it the script says which screens
# stayed closed instead of pretending the walk was complete.
#
# Usage:
#   tools/device_smoke.sh                       # walk the screens of the default package
#   tools/device_smoke.sh --out /tmp/smoke      # where the screenshots go
#   tools/device_smoke.sh --serial ABC123       # pick a device when several are attached
#   tools/device_smoke.sh --monkey 500          # also tap randomly N times (reads the warning)
#   tools/device_smoke.sh --no-su               # never fall back to root
#   tools/device_smoke.sh --list                # print what would be opened, change nothing
#
# Exit codes: 0 clean, 1 findings in the log, 2 the walk did not cover every screen, 3 no adb
# or no device, 4 the screen list itself is broken.

set -u

PACKAGE="cf.playhi.freezeyou"
OUT_DIR="/tmp/freezeyou-smoke"
SERIAL=""
MONKEY_EVENTS=0
LIST_ONLY=0
USE_SU=1
SETTLE=1.5
MANIFEST="$(cd "$(dirname "$0")/.." && pwd)/app/src/main/AndroidManifest.xml"

# Screens that only display something, so opening them is safe with every privilege the build
# might hold. Activities that need an intent to mean anything (a URI to freeze, a package to
# uninstall, a shortcut to create) are deliberately not here: without their intent they close
# immediately and prove nothing.
SCREENS=(
    ".ui.AboutActivity"
    ".ui.BackupMainActivity"
    ".ui.BackupImportChooserActivity"
    ".ui.ManualModeActivity"
    ".ui.ScheduledTasksManageActivity"
    ".ui.ScheduledTasksAddActivity"
    ".ui.ScheduledTaskCommandsSyntaxActivity"
    ".ui.UserDefinedListsManageActivity"
    ".ui.FUFNotificationsManageActivity"
    ".ui.ShowLogcatActivity"
    ".ui.ShowSimpleDialogActivity"
    ".ui.ShortcutLauncherFolderActivity"
    ".ui.AutoDiagnosisActivity"
    ".ui.UriAutoAllowManageActivity"
    ".ui.FirstTimeSetupActivity"
    ".ui.SettingsActivity"
)

usage() {
    sed -n '5,27p' "$0" | sed 's/^# \{0,1\}//'
    exit "${1:-0}"
}

while [ $# -gt 0 ]; do
    case "$1" in
        --out) OUT_DIR="$2"; shift 2 ;;
        --serial) SERIAL="$2"; shift 2 ;;
        --package) PACKAGE="$2"; shift 2 ;;
        --monkey) MONKEY_EVENTS="$2"; shift 2 ;;
        --settle) SETTLE="$2"; shift 2 ;;
        --list) LIST_ONLY=1; shift ;;
        --no-su) USE_SU=0; shift ;;
        -h|--help) usage 0 ;;
        *) echo "Unknown option: $1" >&2; usage 1 ;;
    esac
done

ADB=(adb)
[ -n "$SERIAL" ] && ADB=(adb -s "$SERIAL")

# --- the list itself: no duplicates, and every entry really is in the manifest ---
duplicates=$(printf '%s\n' "${SCREENS[@]}" | sort | uniq -d)
if [ -n "$duplicates" ]; then
    echo "The screen list has duplicates:" >&2
    printf '  %s\n' $duplicates >&2
    exit 4
fi
if [ ! -f "$MANIFEST" ]; then
    echo "Manifest not found at $MANIFEST; run this from the repository." >&2
    exit 4
fi
missing=""
for screen in "${SCREENS[@]}"; do
    if ! grep -q "android:name=\"$screen\"" "$MANIFEST"; then
        missing="$missing $screen"
    fi
done
if [ -n "$missing" ]; then
    echo "These screens are not in the manifest (renamed or removed?):" >&2
    printf '  %s\n' $missing >&2
    exit 4
fi

if [ "$LIST_ONLY" -eq 1 ]; then
    echo "Would open ${#SCREENS[@]} screens of $PACKAGE:"
    printf '  %s\n' "${SCREENS[@]}"
    echo "Screenshots would go to $OUT_DIR"
    [ "$MONKEY_EVENTS" -gt 0 ] && echo "Then monkey would send $MONKEY_EVENTS random events."
    exit 0
fi

if ! command -v adb >/dev/null 2>&1; then
    echo "adb is not on PATH." >&2
    exit 3
fi
if ! "${ADB[@]}" get-state >/dev/null 2>&1; then
    echo "No device (or none selected with --serial)." >&2
    exit 3
fi

mkdir -p "$OUT_DIR"
"${ADB[@]}" logcat -c || true

# --- one screen ---
# am start often prints its refusal on stderr while still exiting 0, so the text is checked as
# well as the code; both the code and the message come back through globals.
START_OK=1
START_MSG=""
start_screen() {   # $1 = component, $2 = 1 to start it through root
    local target="$1" use_root="$2" out rc
    if [ "$use_root" -eq 1 ]; then
        out=$("${ADB[@]}" shell "su -c 'am start -n $target'" 2>&1); rc=$?
    else
        out=$("${ADB[@]}" shell am start -n "$target" 2>&1); rc=$?
    fi
    START_OK=1
    [ "$rc" -ne 0 ] && START_OK=0
    case "$out" in
        *Error*|*Exception*|*"not exported"*|*"Permission Denial"*|*"not found"*) START_OK=0 ;;
    esac
    START_MSG=$(printf '%s\n' "$out" | tr -d '\r' | grep -m1 . || true)
    return 0
}

# Which screen is actually on top. An activity can start and close at once; without this check
# the screenshot would show the screen underneath and the walk would silently count it as covered.
foreground_activity() {
    "${ADB[@]}" shell dumpsys activity activities 2>/dev/null \
        | sed -n 's/.*ResumedActivity[:=] *ActivityRecord{[^}]* \([A-Za-z0-9_.]*\/[^ }]*\).*/\1/p' \
        | head -n1
}

# --- root, if the phone offers it: the only way to open the screens that are not exported ---
SU_AVAILABLE=0
if [ "$USE_SU" -eq 1 ]; then
    su_probe=$("${ADB[@]}" shell "command -v su" 2>/dev/null | tr -d '\r')
    if [ -n "$su_probe" ] && ! printf '%s' "$su_probe" | grep -qiE "not found|no such|inaccessible"; then
        SU_AVAILABLE=1
    fi
fi
if [ "$SU_AVAILABLE" -eq 1 ]; then
    echo "Root is available on the device; it will be used only to open the screens that"
    echo "plain adb refuses. If the phone asks whether to allow root for the shell, allow it."
fi

echo "Opening ${#SCREENS[@]} screens of $PACKAGE; screenshots in $OUT_DIR"
echo

# The main screen starts the process, so the first screen of the walk is not paid for by an
# application that is still starting up.
"${ADB[@]}" shell am start -n "$PACKAGE/.Main" >/dev/null 2>&1 || true
sleep 1
"${ADB[@]}" shell input keyevent KEYCODE_BACK >/dev/null 2>&1 || true

opened=0
not_opened=()

for screen in "${SCREENS[@]}"; do
    name=$(basename "$screen")
    target="$PACKAGE/$screen"
    route="plain adb"

    start_screen "$target" 0
    if [ "$START_OK" -eq 0 ] && [ "$SU_AVAILABLE" -eq 1 ]; then
        start_screen "$target" 1
        route="root (su)"
    fi

    if [ "$START_OK" -eq 0 ]; then
        reason="${START_MSG:-no reason given}"
        case "$reason" in
            *"not exported"*)
                if [ "$SU_AVAILABLE" -eq 1 ]; then
                    reason="not exported, and root did not open it either"
                else
                    reason="not exported: plain adb cannot start it, and no root was found"
                fi ;;
        esac
        printf '  %-36s not opened: %s\n' "$name" "$reason"
        not_opened+=("$name|$reason")
        continue
    fi

    sleep "$SETTLE"
    "${ADB[@]}" exec-out screencap -p > "$OUT_DIR/$name.png" 2>/dev/null

    foreground=$(foreground_activity)
    expected="${screen#.}"
    on_screen=0
    case "${foreground#*/}" in
        *"$expected") on_screen=1 ;;
    esac
    "${ADB[@]}" shell input keyevent KEYCODE_BACK >/dev/null 2>&1 || true
    sleep 0.5

    if [ "$on_screen" -eq 1 ]; then
        opened=$((opened + 1))
        printf '  %-36s opened (%s)\n' "$name" "$route"
    else
        reason="opened, then closed at once (on screen: ${foreground:-unknown})"
        printf '  %-36s %s\n' "$name" "$reason"
        not_opened+=("$name|$reason")
    fi
done

if [ "$MONKEY_EVENTS" -gt 0 ]; then
    cat <<'WARNING'

About to run monkey. Read this first:

  monkey taps at random inside the application, and this application can hold root or Shizuku.
  A stray tap can select applications in a list and freeze them, confirm the "critical packages"
  dialog, or delete scheduled tasks. Revoke the elevated permission before running it on a phone
  you care about, or use a spare device. Without the permission the worst it can do is navigate.

WARNING
    read -r -p "Type yes to continue: " answer
    if [ "$answer" != "yes" ]; then
        echo "Monkey skipped."
    else
        # --pct-syskeys 0 keeps the system buttons out of it, --pct-appswitch 0 stops it from
        # wandering into other applications, and no --ignore-crashes: the first crash stops it.
        "${ADB[@]}" shell monkey -p "$PACKAGE" \
            --pct-syskeys 0 --pct-appswitch 0 --throttle 300 -v "$MONKEY_EVENTS"
        echo "Monkey finished; the log below decides whether it was clean."
    fi
fi

echo
echo "Log since the walk, filtered:"
crashes=$("${ADB[@]}" logcat -d 2>/dev/null \
    | grep -E "FATAL|AndroidRuntime|InflateException|Resources.NotFoundException" \
    | grep -v "logcat -d" || true)
if [ -n "$crashes" ]; then
    printf '%s\n' "$crashes"
    echo
    echo "Findings above."
else
    echo "  nothing: no crash, no inflation failure, no missing resource."
fi

echo
echo "Covered $opened screen(s) of ${#SCREENS[@]}."
if [ "${#not_opened[@]}" -gt 0 ]; then
    echo "Not covered - the log above says nothing about these:"
    for entry in "${not_opened[@]}"; do
        printf '  %-36s %s\n' "${entry%%|*}" "${entry#*|}"
    done
    echo "Open them by hand, or run again with root allowed. Screenshots are in $OUT_DIR."
else
    echo "Screenshots are in $OUT_DIR. The log cannot tell you whether a screen looks right -"
    echo "that part is the owner's eye on those images."
fi

if [ -n "$crashes" ]; then exit 1; fi
if [ "${#not_opened[@]}" -gt 0 ]; then exit 2; fi
exit 0
