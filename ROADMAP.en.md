# Fork roadmap

A working list for the personal fork `tommynok/FreezeYou`. Not upstream, and none of it is a
proposal to upstream — it is written down so the fork's own decisions are not made twice.

Sections are ordered by how often they are needed: what to fall back to and what is still
unverified first, reference material after that, history last.

**This file is a snapshot for sharing, translated from `ROADMAP.md`, which is the source of
truth.** Where the two disagree, the Russian one is right. Snapshot taken 24 September 2026, at
build 128.

---

## Fallback points

| | What it is |
|---|---|
| `build-115` (`7c39824`) | Provisionally stable. Confirmed working in a quick check: long activity lists open without delay, a shortcut to a non-exported activity starts. No full pass yet. |
| `stable-2026-08-13` (`89d485c`, run #76) | Older, verified in full. The last state before the speed work. |

Falling back is `git checkout build-115`; the APK of that same build is attached to the release
and installs from the phone. No branch is kept for an intermediate stable point: a release tag
already names the commit and does not move, while a branch moves and needs tending.

---

## Still to verify on a device

The only list of open checks. Unit tests are not part of it — they run in CI before every build.

### Settings, after the menu rework (builds 110–115)

- [ ] Walk every section: nothing lost, nothing duplicated, every row has its summary.
- [ ] "Access and security": the lock turns on and off (biometrics are asked for), and toggling
      the launcher icons really shows and hides them.
- [ ] "Freeze and unfreeze": dependent rows grey out when the shortcut automation is off.
- [ ] "Other settings" → "Danger zone" opens, and its text is about FreezeYou itself.
- [ ] "Automation" → "Delete all tasks": the system does not wake the application afterwards on
      the schedule of tasks that no longer exist.
- [ ] Switch colour scheme and language several times — the settings lists must not stack.
- [ ] The dot at the end of a row: select an **un**frozen application in the light theme, there
      must be no pale dot.

### Unloaded menu (build 116)

- [ ] The floating "+" no longer offers Settings, FAQ and About; everything else is there.
- [ ] The action bar overflow still has all three.

### Activity shortcut (builds 117, 120–123, 127)

- [ ] "Create an activity shortcut" is the second row of an application's menu.
- [ ] Tapping it opens the activity list straight away, with no screen in between.
- [ ] No "Task" and "ID" fields on the page; name and icon are filled in.
- [ ] "Test" starts the activity, "Create" pins the shortcut.
- [ ] The shortcut starts the activity and does **not** unfreeze the application.
- [ ] A non-exported activity starts through root/Shizuku.
- [ ] **A frozen application: the activity list opens at all.** This was broken until build 127.
- [ ] A frozen application: the dialog offers to unfreeze; confirming unfreezes and starts,
      cancelling just closes.
- [ ] The dialog text is one paragraph and readable at a glance; the name it quotes matches the
      menu entry it points at.
- [ ] Leave the activity list with Back, then create the shortcut — the existing freeze shortcut
      of the same application is still there.
- [ ] Two shortcuts to different activities of one application do not overwrite each other.
- [ ] "Unfreeze only" is not offered in the list in this mode.
- [ ] The list opens for a huge package (Google Play services, Settings).

### Crash log and loading indicator (builds 118, 119)

- [ ] The dialog after a crash asks whether to share the log, with a Share button.
- [ ] The log reaches a messenger as text and a file manager as an attachment.
- [ ] The same log is not offered again on the next launch.
- [ ] Activities that used to kill the process now either start or report.
- [ ] A spinner is visible while the activity list is built and goes when the list arrives.

### Not checked and not planned

- "Automatic freeze/unfreeze" on tapping a row — the mode is not used.

---

## Open questions

### Deferred

- **Undo after a bulk operation.** There are "frozen" filters but not by date, so there is
  nothing to roll the last batch back with.
- **Offline help, sections 7, 7.1, 7.2.** Only the scheduled task command syntax is translated.
- **A pinned "My selection" section at the top of the main list.** Needs the list adapter reworked.
- **Search in settings.** There are many settings and the only way to find one is to walk every
  section.

### Known weak spots

- **A stale list of notifying packages.** It is cleared in `onListenerDisconnected`, but if the
  process is killed without that call and notification access is revoked afterwards, the list
  stays for ever — and those applications quietly stop being freezable. The honest fix is to ask
  the system about the permission at the moment of the check; what stands in the way is that
  `isAvoidFreezeNotifyingApplicationsEnabledAndAppStillNotifying` gets no `Context`, and there are
  five call sites. A TTL is not an option: a VPN client's notification can go hours without an
  update and would wrongly expire, breaking exactly the case that was being protected.
- **A "Launch" shortcut target is stored as the absence of a value** rather than an explicit
  marker. It works, but it is less obvious than the `@onlyUnfreeze` next to it.
- **Statistics in the root batch path count every package in the batch**, including the ones
  skipped (foreground, notifying, already frozen): that path writes all commands into one `su`
  and only checks the overall exit code, so it has no way of knowing what actually happened.
- **Existing statistics counters are one short**, a consequence of the bug described under
  "Post-mortems"; there is no way to repair them after the fact.
- **`hiddenapi: … denied` in the log while the calls work.** Printed once per process for
  `setApplicationEnabledSetting`, and every package still settles. It does not affect the result,
  but it is worth understanding: either the warning is about a different signature, or
  `obtainCachedPackageManagerProxy` takes a route that was not considered.
- **Notifications after an unfreeze cost about 1310 ms for 24 applications.** Already off the
  critical path; worth touching again only if they visibly pile up in the shade. The next step, if
  it comes to that, is loading the icons on several threads — they are independent, and unlike the
  package manager there is no serialisation on the system side.

---

## Tests

`./gradlew testReleaseUnitTest`, on the JVM, no phone or emulator. They run in CI before the
build and are not part of the APK. The only dependency is JUnit 4 — no Robolectric, no mocks — so
only what does not touch Android can be covered.

| Test | What it guards |
|---|---|
| `ShortcutTargetTest` | A shortcut's target survives a language change: "Launch" → empty, "Only unfreeze" → `@onlyUnfreeze`, a class name kept verbatim. This is where shortcuts used to break. |
| `SearchRankingTest` | Search order: start of the label, then anywhere in the label, then the package name. |
| `SettingsKeyWiringTest` | Every settings row's `app:key` against the enum constant names in `storage/key`. |
| `NotifyingListTest` | Batch removal from the notifying list: "com.foo" must not take "com.foo.bar" with it. |

`SettingsKeyWiringTest` reads the names from source rather than by reflection: loading those
classes drags in Android and MMKV for what is a comparison of two sets of strings. Both new tests
fail if they find no settings rows or no keys, so they cannot pass vacuously.

**Not covered, needs Robolectric or a SQLite stand-in:** `TasksUtils.collectTriggeredTasks`
(trigger matching, an empty `tgextra` meaning "any application", `[cpkgn]` substitution, the batch
variant), `OneKeyListUtils.decodeUserListsInPackageNames` (which had a bug with an empty package
name), `CriticalPackagesUtils.findCriticalPackages`, and the statistics counter. Bringing in
Robolectric for that is not worth it yet.

**Not coverable at all:** freezing through Shizuku, and everything going through `Freeze` and
`processBatchAction` — those need a real device and hold no pure function worth isolating.

`tools/check_layout_variants.py` and `tools/check_settings_keys.py` overlap with the tests on
purpose: the development sandbox has no Android SDK and cannot run Gradle, so the checks run as
scripts before a push and as tests in CI. The scripts themselves have no tests.

---

## How the finished pieces work

Reference material: why things are the way they are, so they are not undone.

### The activity shortcut

No separate activity was added: the shortcut leads into the same `Freeze` with `justLaunch=true`.
`FreezeActivityViewModel.go()` checks that flag first and hands over to
`checkAndStartTaskAndTargetAndActivityOfUnfrozenApp`, past the freezing, the dialog and the
animation. That way the shortcut inherits the lock screen, the recents behaviour and the task
description.

A flag rather than a marker in the target string, the way `@onlyUnfreeze` is done: for this kind
of shortcut the target already holds the activity's class name, so there is nowhere to put one.

A frozen application is disabled in the package manager and none of its components will start —
root does not help, it lifts the restriction on non-exported components, not on disabled ones. So
an activity of a frozen application cannot be started without unfreezing it first.

At first the shortcut simply said so, which left it useless on frozen applications — that is, on
exactly the ones the utility exists for. It now offers to unfreeze:
`Freeze.buildAndShowUnfreezeToLaunchDialog`, two buttons, and confirming runs the same
`fufAction(..., runImmediately = true, frozen = true)` as the ordinary unfreeze shortcut, with the
animation and the target launch. It asks rather than doing it silently, because unfreezing changes
state and the application cannot know whether that is wanted.

The name of the other kind of shortcut, quoted in the dialog, is taken from that command's own
string resource so the two cannot drift apart.

### Sharing the crash log

The dialog offered one destination: the upstream author's crash report page. For a fork that is of
no use. `Main.shareCrashLog` hands the log to the system share sheet in two forms at once —
`EXTRA_TEXT` for messengers and note apps, `EXTRA_STREAM` through `FileProvider` for file managers
and mail.

Two traps, both accounted for:

- `res/xml/file_paths.xml` only had `external-cache-path`, while the log is written to the
  internal cache. `cache-path` was added, without which `FileProvider.getUriForFile` throws.
- The log file used to be deleted as soon as the dialog was shown, and the receiving application
  reads the uri after the dialog is gone. Only the `NeedUpload.log` pointer is dropped immediately
  now, so the same log is not offered twice; the file itself goes when the offer is declined.
  After sharing it stays in the cache, which the system reclaims.

### The settings menu

There used to be 16 top-level rows with not one summary between them, holding anywhere from 1 to
11 entries each, about 60 settings in all, and three settings lived in two places at once. There
are now 13 rows in three groups, each saying what is inside.

- **Settings**: Main screen · Appearance · Freeze and unfreeze · Automation · Notifications ·
  Access and security · Install and uninstall.
- **Maintenance**: Manage space · Backup · Diagnosis · Other settings.
- **Help**: How to use · About.

Gone as separate screens: "Common", "Security", "Icons and entrances", "Background service", and
the intermediate notifications screen. No setting was lost — every `app:key` was cross-checked.

The visible name of the freeze-on-quit list appears in seven places, while the list's internal key
(`New_FreezeOnceQuit`) does not depend on it and is marked untranslatable — so renaming it, if that
is ever wanted, will not break existing lists.

The button in the danger zone calls `ActivityManager.clearApplicationUserData()`, which is exactly
"Clear data" from the system settings. The danger zone stayed a screen of its own deliberately: a row that erases data should not sit in
the same list as a debug switch. Its text says plainly that it erases FreezeYou's own data and
that frozen applications stay frozen, because the system holds that state, not FreezeYou.

One trap: the "background service" switch used to be hidden on Android O+ by removing the **whole
screen** under the key `backgroundService`. There is no such screen now, so the row itself is
removed — in `SettingsFufFragment`, not in `SettingsFragment`.

### Deleting every scheduled task

`TasksUtils.deleteAllScheduledTasks` cancels the alarms before deleting the databases — otherwise
the system kept waking the application on the schedule of tasks that no longer existed. Timed
tasks are cancelled by the row's `_id`; delayed ones by the request codes recorded in Tray under
`OSA_<package>`, `OLA_<package>`, `onScreenOn` and `onScreenOff`, because a delayed task's code is
not its `_id` — it is made up when the task is scheduled, and there is no other way to find it.

### Batch operations

Single and batch actions from the main list run in the main process
(`FUFUtils.processSingleActionInProcess` and `processBatchAction`). `FUFService` remains for
shortcuts, widgets, the scheduler and the root batch modes.

The order of the batch tail matters and was chosen by measurement: statistics → tell the list to
refresh → triggered tasks → toast, and the quick notifications **only after that**. They cost more
than the work itself and nothing depends on them — see "Post-mortems".

### Greyed icons for frozen applications

One shared `ColorMatrixColorFilter` on the `ImageView` in `MainAppListSimpleAdapter.getView`, with
no copies of the icons. Three things already learned the hard way:

- `getView` recycles views and `setImageDrawable` does not clear the filter, so the filter is
  removed as explicitly as it is set.
- The setting is re-read in `Main.updateFrozenStatus`, that is on every `onResume`: the adapter
  outlives a trip to the settings and back, and without that the switch only took effect after a
  restart.
- The frozen flag is a separate `Frozen` key, because `isFrozen` is the resource id of the dot and
  depends on the theme.

### The dot at the end of a row

`getThemeSecondDot` always returns `shapedot_transparent`. For unfrozen applications the dot used
to be filled with the window background, making it invisible — except on the grey background of a
selected row, where it became a visible pale circle, a second marker beside the real one. The
transparent dot has the same size and margins, so rows measure as before;
`drawable-v21/shapedot_colorbackground.xml` is gone along with the per-theme selection that existed
only to imitate the background.

### The freeze animation

It has its own duration (`ANIMATION_DURATION_MILLIS`, 450 ms) and the `Freeze` window is held open
until it finishes (`finishWhenAnimationIsOver`). It used to be the measured average of the last
five operations: while an operation took about half a second that worked, but after the speed work
the average collapsed to its 200 ms floor and the animation became a blink. The timing and the
`AverageTimeCostsKV` store were removed.

---

## Post-mortems

### Batch freezing — the bookkeeping cost more than the work

Measured on a device (Shizuku, 25 applications). First column is the package manager calls, second
is everything after the loop:

| | Calls | Tail |
|---|---|---|
| Freeze | 121 ms | ~960 ms |
| Unfreeze | 168 ms | ~580 ms |

Two conclusions, both against the guesses made before measuring:

1. `verifyFrozenStateSettled` costs almost nothing — 5 ms per package is the call **together**
   with the verification, so the polling loop never runs. "Two passes" and "a finer poll step"
   would have gained zero. **Do not do them.**
2. The bottleneck is the bookkeeping after the loop. Three sources, all fixed:

- **Statistics outside a transaction.** `DataStatisticsUtils.addTimes` ran one `execSQL` per package, each its own transaction with
  a journal flush — 25 flushes instead of one — plus the create-table statement per package. Now:
  one transaction per batch, the table created once.
- **The notifying list in Tray.** `deleteNotification` read and rewrote one string per package,
  and Tray is a ContentProvider: a round trip and a database write per call. That is the whole
  difference between freezing and unfreezing, about 380 ms, roughly 15 ms per package. Now
  `deleteNotifications(context, list)` in a single pass.
- **Quick notifications after an unfreeze** (`checkAndCreateFUFQuickNotification`). Per application: a package manager lookup, an icon
  loaded or decoded, a bitmap drawn, a binder transaction. 1413 ms against 237 ms for the
  unfreezing itself; two unfreezes in a row measuring 184 ms and 1413 ms is the same cause, warm
  icons against cold. Now the loop runs after the toast.

The root batch path (`oneKeyActionRoot`) had the same tail and worse: `onFApplications` opened the
statistics database **and** scanned the trigger table per package. It uses the batch calls now,
over the same set of packages, with no change in behaviour.

**Result, confirmed on the device**, build 126, 24 applications:

| | actions | bookkeeping | reportedAfter | notifications |
|---|---|---|---|---|
| Unfreeze | 139 ms | 16 ms | **155 ms** (was 1650) | 1310 ms |
| Freeze | 115 ms | 75 ms | **190 ms** (was ~1080) | 29 ms |

`reportedAfter` is the time until the toast, which is what is actually felt. The debug line prints
all four separately, so moving the notifications aside does not hide their cost.

**How to measure.** Turn on debug mode under "Other settings", run the operation, open the log
viewer and look for the `DebugModeLogcat` tag: `binder/call/verify` per package and
`actions/bookkeeping/reportedAfter/notifications` per batch. If debug mode is silent, the system
itself provides a scale: it prints a `JavaBinder`/`Parcel` pair for every
`setApplicationEnabledSetting` call, and the first measurement was taken from their timestamps.

### Debug mode could never be turned on

The switch in `spr_advance.xml` carried `app:key="DebugModeEnabled"` while the enum constant is
called `debugModeEnabled`. The chain breaks in the middle, silently:

1. The switch writes the SharedPreferences key `DebugModeEnabled`.
2. `SettingsUtils.convertToAbstractKey` tries four enums through `valueOf`, each throws
   `IllegalArgumentException`, and it returns `null`.
3. `checkPreferenceData` simply returns on `null` — nothing reaches MMKV.
4. `isDebugModeEnabled()` reads MMKV under `debugModeEnabled` and always gets `false`.

It surfaced only because the switch was reported as enabled while the log had no debug lines in
it. The key is fixed, and the check now exists as both a script and a test.

### Why launching an activity crashed

`checkAndStartTaskAndTargetAndActivityOfUnfrozenApp` caught only `SecurityException`, the
non-exported case. A component the system will not resolve — disabled in its manifest, or read out
of the APK and unknown to the package manager — raises `ActivityNotFoundException`, which was
caught nowhere: the uncaught exception reached `CrashHandler`, which killed the process. Hence a
black screen on **some** activities rather than all. That case now takes the elevated path, and any
other exception is reported. The link to the specific crash observed remains a hypothesis until
there is a log of it.

### Settings lists drawn on top of each other

`SettingsActivity.onCreate` replaced the content with a new `SettingsFragment`
**unconditionally**. On `recreate()` — which a change of theme, language or "follow the system
dark theme" triggers — the system restores the fragments itself and another one was laid over the
restored one. Fixed by only adding the fragment when `savedInstanceState == null`. Along the way,
`onPreferenceStartScreen` was putting its fragment into `R.id.content` rather than
`android.R.id.content`, unlike every other transaction; that path is dead now, but the mismatch
was corrected.

### The statistics counter started at zero

`addTimes` inserted a new row with `times = '0'`, so every application's first freeze went uncounted. It
is `'1'` now. Existing rows are left alone: there is no way to tell which of them this produced.

---

## Code reviews

### 22 September, from the stable point, 35 files

Six findings, each verified by reading the code. Fixed: an ANR in the activity picker
(`SelectTargetActivityActivity.init()` called `getPackageArchiveInfo` and `loadIcon`/`loadLabel`
per row straight from `onCreate`); a shortcut to a non-exported activity not starting (the elevated
launch had been added to `FUFUtils.checkAndStartApp` but not to the shortcut's path); a toast
through a dead Activity (`startActivityElevatedAsync` held the given context for as long as `su`
blocked, and uses `applicationContext` now); deleting all tasks on the main thread with a leaked
`Cursor`; a stale foreground package after accessibility access is revoked, now cleared in
`AccessibilityService.onUnbind`. One was left open — the stale notifying list, see "Known
weak spots".

What the review could not catch: the connection between the faster freezing and the degenerate
animation. That is not a mistake in one place but a consequence of a change made a month earlier in
another file.

### 24 September, range `build-115..HEAD`

Five findings, four confirmed and fixed.

- **The activity list would not open for a frozen application** — introduced during that same
  session: `getApplicationInfo(pkgName, 0)` without `GET_UNINSTALLED_PACKAGES` does not find a
  disabled package. The feature broke on exactly the applications it exists for. The same missing
  flag was in `getActivitiesFromApk`, where it predates this work.
- **An activity shortcut could overwrite a freeze shortcut:** it was handed an id of the form
  `"FreezeYou! <package>"`, which is the freeze shortcut's, one per package, and leaving the
  picker with Back kept it. No id is passed now and one is generated.
- **A failed launch said nothing** — the result code was discarded. It gets its own message,
  `cannotFindTheEntrance`, rather than the existing one, which speaks of an unfreeze that
  succeeded and did not happen here.
- **"Unfreeze only" in the activity list** produced a shortcut that does nothing. It is no longer
  offered in that mode.

**Left alone, deliberately:** `mShowUnfreezeToLaunchDialog` is never reset and, the review argued,
would give two stacked dialogs after a rotation. `Freeze.onPause` calls `finish()` unless the
screen is locked, so a configuration change ends the activity instead of redelivering to it, and
the scenario does not reproduce. The long-standing `mShowDialog` has the same shape. Should
`onPause` ever stop closing the activity, the finding becomes real.

---

## Rules paid for with mistakes

- **Look at the view class's defaults and at the neighbouring button on the same screen.** Unlike
  `ImageView`, `ImageButton` defaults its `scaleType` to `center`, not `fitCenter`: the image is
  drawn at its own size and does not grow with the button. `ic_add_alarm` and `ic_explore` are 32 dp PNGs, and in a 45 dp circle they floated
  in empty space. Correct: `scaleType="fitCenter"` and **no** `padding`, which only subtracts from
  the size. This cost three builds while the main "plus" button next to it had the answer all along.
- **Measure instead of guessing.** Two theories about where the batch spent its time were refuted
  by the measurement, and nobody named the real cause before the log arrived.
- **Check the code rather than trust an impression.** "Debug mode was off" was stated without
  checking and was wrong — it could not be turned on because of a bug.
- **A setting that silently does nothing is a whole class of bug.** Caught by
  `tools/check_settings_keys.py` and `SettingsKeyWiringTest`.

---

## Done and confirmed on the device

| What | Where the details are |
|---|---|
| Single actions in the main process, across every row-tap mode | verified on the device |
| Protection against freezing notifying and foreground applications | broke when moved to another process, fixed with shared state through multi-process MMKV |
| Greyed icons for frozen applications, in list and grid (`greyFrozenApplicationsIcons`) | "How the finished pieces work" |
| The freeze and unfreeze animation | same section |
| The shortcut freeze dialog | never lost: the "show fewer toasts" setting (`lesserToast`) silenced the success message, and only that |
| Scheduled task syntax help on its own screen, without version notes | — |
| The two small buttons on the tasks screen, 35 → 45 dp | "Rules paid for with mistakes" |
| Releases instead of artifacts, tagged `build-<number>` | `CLAUDE.md` |
| The settings menu rework | "How the finished pieces work" |
| The unloaded floating "+" | one menu resource for both, rare entries hidden in `onPrepareMainOptionsMenu` |
| Faster batch operations | "Post-mortems" |
