#!/usr/bin/env bash
#
# Opens every screen of the installed build once, takes a screenshot of each, and then looks at
# the log for crashes and inflation failures. It taps nothing: opening a screen cannot freeze,
# uninstall or delete anything, which is what makes this the safe half of a smoke test. The
# random-tap half (monkey) lives in a separate mode below, and it is the one that can misbehave
# on a build that holds root, so it is opt-in and refuses to run without --monkey.
#
# Usage:
#   tools/device_smoke.sh                       # walk the screens of the default package
#   tools/device_smoke.sh --out /tmp/smoke      # where the screenshots go
#   tools/device_smoke.sh --serial ABC123       # pick a device when several are attached
#   tools/device_smoke.sh --monkey 500          # also tap randomly N times (reads the warning)
#   tools/device_smoke.sh --list                # print what would be opened, change nothing
#
# Exit code is 0 only when no crash was found in the log.

set -u

PACKAGE="cf.playhi.freezeyou"
OUT_DIR="/tmp/freezeyou-smoke"
SERIAL=""
MONKEY_EVENTS=0
LIST_ONLY=0
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
    sed -n '2,20p' "$0" | sed 's/^# \{0,1\}//'
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

echo "Opening ${#SCREENS[@]} screens of $PACKAGE; screenshots in $OUT_DIR"
failed_to_start=0
for screen in "${SCREENS[@]}"; do
    name=$(basename "$screen")
    if ! "${ADB[@]}" shell am start -n "$PACKAGE/$screen" >/dev/null 2>&1; then
        printf '  %-38s could not be started (not exported, or it needs an intent)\n' "$name"
        failed_to_start=$((failed_to_start + 1))
        continue
    fi
    sleep "$SETTLE"
    "${ADB[@]}" exec-out screencap -p > "$OUT_DIR/$name.png" 2>/dev/null
    "${ADB[@]}" shell input keyevent KEYCODE_BACK >/dev/null 2>&1
    sleep 0.5
    printf '  %-38s opened\n' "$name"
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
    echo "Findings above. Screenshots are in $OUT_DIR for the visual half of the check."
    exit 1
fi

echo "  nothing: no crash, no inflation failure, no missing resource."
echo
echo "Screenshots are in $OUT_DIR. The log cannot tell you whether a screen looks right -"
echo "that part is the owner's eye on those images."
exit 0
