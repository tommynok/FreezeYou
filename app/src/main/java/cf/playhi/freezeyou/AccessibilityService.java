package cf.playhi.freezeyou;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.os.Build;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;

import androidx.annotation.CallSuper;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import cf.playhi.freezeyou.ui.AppLockActivity;
import cf.playhi.freezeyou.utils.DataStatisticsUtils;
import cf.playhi.freezeyou.utils.InstallPackagesUtils;
import cf.playhi.freezeyou.utils.OneKeyListUtils;
import cf.playhi.freezeyou.utils.ServiceUtils;
import cf.playhi.freezeyou.utils.ProcessSharedState;
import cf.playhi.freezeyou.utils.TasksUtils;

import static cf.playhi.freezeyou.storage.key.DefaultMultiProcessMMKVStorageBooleanKeys.freezeOnceQuit;
import static cf.playhi.freezeyou.storage.key.DefaultMultiProcessMMKVStorageBooleanKeys.lockOnScreenOff;
import static cf.playhi.freezeyou.storage.key.DefaultMultiProcessMMKVStorageStringKeys.appLockReLockPolicy;
import static cf.playhi.freezeyou.utils.AuthenticationUtils.isAppLockEnabled;
import static cf.playhi.freezeyou.utils.AuthenticationUtils.isAuthenticatorAvailable;
import static cf.playhi.freezeyou.utils.FUFUtils.processFreezeAction;
import static cf.playhi.freezeyou.utils.OneKeyListUtils.existsInOneKeyList;
import static cf.playhi.freezeyou.utils.Support.checkLanguage;
import static cf.playhi.freezeyou.utils.Support.getLocalString;
import static cf.playhi.freezeyou.utils.TasksUtils.cancelAllUnexecutedDelayTasks;

/**
 * Foreground application monitor and automation trigger engine backed by Android's Accessibility framework.
 *
 * <p><b>Architecture &amp; Rationale:</b><br>
 * Detecting the active foreground application on modern Android without continuous CPU-heavy polling
 * (e.g. {@code UsageStatsManager}) is achieved efficiently via accessibility window change events
 * ({@link AccessibilityEvent#TYPE_WINDOW_STATE_CHANGED}). This service tracks window focus transitions
 * in real time to power:
 * <ul>
 *   <li><b>"Freeze Once Quit" Automation:</b> Freezes configured applications immediately when the user
 *       navigates away from them.</li>
 *   <li><b>Foreground Guard:</b> Protects the currently viewed application from being frozen by background
 *       timers or scheduled tasks.</li>
 *   <li><b>Event Triggers:</b> Dispatches {@code onApplicationsForeground} and {@code onLeaveApplications}
 *       rules defined in the automation trigger database.</li>
 *   <li><b>Application Lock:</b> Throws {@code AppLockActivity} over an application on the protected list
 *       the moment its window takes the screen, whichever way the user reached it - launcher icon,
 *       system settings, a notification, or the recent-apps overview. An application that has to stay
 *       unfrozen to receive its messages cannot be locked by freezing it, and a window change that is
 *       already being listened for costs no more battery to act on than to read.</li>
 * </ul>
 *
 * <p><b>OEM Overlay Filtering (MIUI / HyperOS Quirks):</b><br>
 * Many OEM skins (particularly Xiaomi's MIUI/HyperOS) dispatch full-screen window state events for
 * transient UI elements like volume sliders, floating sound assistants, and input method windows.
 * Without explicit heuristic filtering, adjusting the media volume or typing on a soft keyboard would
 * erroneously register as "exiting the app", causing the active application to freeze abruptly while
 * in use.
 */
public class AccessibilityService extends android.accessibilityservice.AccessibilityService {

    @Override
    @CallSuper
    protected void attachBaseContext(Context newBase) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            String locale = getLocalString(newBase);
            Configuration configuration = new Configuration();
            configuration.setLocale(
                    "Default".equals(locale) ? Locale.getDefault() : Locale.forLanguageTag(locale)
            );
            Context context = newBase.createConfigurationContext(configuration);
            super.attachBaseContext(context);
        } else {
            super.attachBaseContext(newBase);
        }
    }

    @Override
    public void onCreate() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            checkLanguage(this);
        }
        super.onCreate();
        // Nothing was carried into this session: the list of applications the user was let into is
        // written by whichever process raised the last lock screen, and a service that starts now
        // is the first thing that could say what is actually on screen.
        endUnlockedSession();
        registerScreenOffLockReceiver();
    }

    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private final Map<String, Long> mPackageDepartures = new ConcurrentHashMap<>();
    private BroadcastReceiver mScreenOffReceiver;
    /**
     * Whether the screen this service raised over somebody else's application is still the one in
     * front. The events arrive on one thread and only this class reads or writes it.
     */
    private boolean mAppLockIsUp = false;
    /**
     * The application a lock screen was raised over, whose leave has not been handled yet.
     *
     * The window that takes the screen after such a lock is raised is this application's own, and
     * answering it as a departure would freeze or run the exit tasks of an application the user is
     * still being asked about. So the owed leave is recorded here and paid out when a window of
     * somebody else finally takes the screen.
     */
    private String mPendingAppLockPkg = null;

    @Override
    public void onDestroy() {
        if (mScreenOffReceiver != null) {
            unregisterReceiver(mScreenOffReceiver);
            mScreenOffReceiver = null;
        }
        endUnlockedSession();
        // A service with no more events to read has no more chances to pay an owed leave, and the
        // next one starts from a screen it did not raise.
        mPendingAppLockPkg = null;
        mExecutor.shutdown();
        super.onDestroy();
    }

    /**
     * A screen that went dark ends the session the user was let into: whoever wakes the device
     * next is not necessarily the person who authenticated, and the applications they were reading
     * are still in the foreground waiting for them.
     */
    private void registerScreenOffLockReceiver() {
        mScreenOffReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (isAppLockEnabled() || lockOnScreenOff.getValue(null)) {
                    endUnlockedSession();
                }
            }
        };
        registerReceiver(mScreenOffReceiver, new IntentFilter(Intent.ACTION_SCREEN_OFF));
    }

    /**
     * The applications the user was let into are forgotten - and with them the window they were let
     * into.
     *
     * The lock goes up on a change of package, so a session that ends while a protected application
     * is still the one in front has to stop reporting it as in front. Otherwise a device woken to
     * that same screen names the package it already had, the event reads as no change, and the
     * application the user was stopped from reading stays open for whoever wakes it next.
     */
    private void endUnlockedSession() {
        mPackageDepartures.clear();
        ProcessSharedState.clearAllUnlockedPackages();
        MainApplication.clearCurrentPackage();
        mAppLockIsUp = false;
    }

    /**
     * Intercepts window state transitions and identifies genuine application switches.
     *
     * <p><b>Filtering Pipeline:</b>
     * <ol>
     *   <li>Ensures the event represents a full-screen window change ({@code isFullScreen()}).</li>
     *   <li>Filters out system packages ({@code android}, {@code com.android.systemui}, {@code com.android.packageinstaller}).</li>
     *   <li>Filters out OEM overlays (e.g. {@code miui.systemui.plugin}, MIUI volume panel dialogs).</li>
     *   <li>Filters out soft keyboards ({@code SoftInputWindow}) to prevent freezing apps while typing.</li>
     *   <li>Validates that the screen is actively illuminated ({@code pm.isScreenOn()}).</li>
     *   <li>If the user switched to a different package, updates [MainApplication#getCurrentPackage],
     *       raises the application lock over a protected package they have just entered and locks
     *       again the one they left, then dispatches exit triggers ([onLeaveApplications]) and entry
     *       triggers ([onApplicationsForeground]).</li>
     * </ol>
     */
    @Override
    public void onAccessibilityEvent(AccessibilityEvent accessibilityEvent) {
        int type = accessibilityEvent.getEventType();
        switch (type) {
            case AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED:

                CharSequence pkgName = accessibilityEvent.getPackageName();
                if (pkgName == null) return;
                String pkgNameString = pkgName.toString();
                if ("".equals(pkgNameString)) return;

                CharSequence className = accessibilityEvent.getClassName();
                String classNameString = className != null ? className.toString() : null;

                // Soft keyboards and volume sliders are in-app transient overlays:
                if (isTransientInputOverlay(this, pkgNameString, classNameString)) {
                    return;
                }

                // System runtime permission prompts (e.g. camera, contacts permission dialogs)
                // belong to the foreground workflow and must not count as exiting the app:
                if (isPermissionDialog(pkgNameString)) {
                    return;
                }

                PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
                if (pm == null) return;
                if (!pm.isScreenOn()) return;

                String previousPkg = MainApplication.getCurrentPackage();

                if (pkgNameString.equals(previousPkg)) {
                    if (shouldSkipSamePackageTransition(
                            isOnAppLockList(pkgNameString),
                            ProcessSharedState.isPackageUnlocked(pkgNameString),
                            appLockShouldIntercept(
                                    isAppLockEnabled(), isAuthenticatorAvailable(this),
                                    ProcessSharedState.isPackageUnlocked(pkgNameString)
                            )
                    )) {
                        return;
                    }
                }

                MainApplication.setCurrentPackage(pkgNameString);

                if (getPackageName().equals(pkgNameString)) {
                    // The window of this application is the lock screen raised over an application
                    // the user has not been let into yet. They have not left that application - the
                    // screen in front of them belongs to this one, and answering it as a departure
                    // would freeze it or run its exit tasks while the question is still open.
                    if (mAppLockIsUp) return;
                } else if (isReturningFromAppLock(
                        mAppLockIsUp, previousPkg, pkgNameString, mPendingAppLockPkg, getPackageName())) {
                    mAppLockIsUp = false;
                    String pendingLockPkg = mPendingAppLockPkg;
                    mPendingAppLockPkg = null;

                    if (!ProcessSharedState.isPackageUnlocked(pkgNameString)) {
                        // The user dismissed, cancelled, or backed out of the lock screen without
                        // proving themselves. Never re-raise the lock over the application they
                        // refused to unlock (which would trap them in an infinite loop), and never
                        // let them into it: send them safely to the home screen and handle pending quit.
                        if (pendingLockPkg != null && !"".equals(pendingLockPkg)) {
                            freezePendingLockQuit(pendingLockPkg);
                            mExecutor.execute(() -> onLeaveApplications(pendingLockPkg, pkgNameString));
                        }
                        leaveTowardsHome();
                        return;
                    }

                    // Authentication succeeded: clear any stale departure time and let the user into
                    // the application without re-evaluating session expiry or re-raising the prompt.
                    mPackageDepartures.remove(pkgNameString);
                } else {
                    mAppLockIsUp = false;

                    // Somebody else has taken the screen from behind this application's own window.
                    // If an application the user was stopped at still has no leave recorded, this is
                    // the moment it has one: they were never let into it, and it is still sitting
                    // open behind everything they are looking at now.
                    String pendingLockPkg = mPendingAppLockPkg;
                    mPendingAppLockPkg = null;
                    if (pendingLockOwesDeparture(
                            pendingLockPkg, ProcessSharedState.isPackageUnlocked(pendingLockPkg))) {
                        freezePendingLockQuit(pendingLockPkg);
                        mExecutor.execute(() -> onLeaveApplications(pendingLockPkg, pkgNameString));
                    }

                    // The application the user was let into is locked again the moment it stops
                    // being the window on screen. A window of this application is not that moment:
                    // the lock screen this service raises is one, and treating it as a departure
                    // would ask again before the answer had reached the application behind it.
                    if (previousPkg != null && !"".equals(previousPkg) && !getPackageName().equals(previousPkg)) {
                        mPackageDepartures.put(previousPkg, SystemClock.elapsedRealtime());
                        if (shouldRelockOnAppExit(appLockReLockPolicy.getValue(null))) {
                            ProcessSharedState.clearPackageUnlocked(previousPkg);
                        }
                    }

                    if (isOnAppLockList(pkgNameString)) {
                        String policy = appLockReLockPolicy.getValue(null);
                        Long departure = mPackageDepartures.get(pkgNameString);
                        if (isAppLockSessionExpired(policy, departure, SystemClock.elapsedRealtime())) {
                            ProcessSharedState.clearPackageUnlocked(pkgNameString);
                            mPackageDepartures.remove(pkgNameString);
                        }

                        if (appLockShouldIntercept(
                                isAppLockEnabled(), isAuthenticatorAvailable(this),
                                ProcessSharedState.isPackageUnlocked(pkgNameString))) {
                            // From here this service answers for the way out of that application, whether
                            // the screen comes up over it or not.
                            mPendingAppLockPkg = pkgNameString;
                            if (!showAppLock(pkgNameString, previousPkg)) {
                                // Nothing covers this application and the user is being taken away from
                                // it, so the switch is not a visit: no usage counted for it, no exit
                                // task run and no entry rule fired on the strength of a window the user
                                // was refused.
                                return;
                            }
                            mAppLockIsUp = true;
                            return;
                        } else {
                            mPackageDepartures.remove(pkgNameString);
                        }
                    }
                }

                addUpUseTimes(pkgNameString);

                if (!isSystemUiOverlay(this, pkgNameString, classNameString)
                        && freezeOnceQuit.getValue(null)
                        && existsInOneKeyList(getApplicationContext(), getString(R.string.sFreezeOnceQuit), previousPkg)) {
                    processFreezeAction(
                            getApplicationContext(), previousPkg, null, null,
                            false, null, false
                    );
                }

                mExecutor.execute(() -> {
                    onLeaveApplications(previousPkg, pkgNameString);
                    onApplicationsForeground(previousPkg, pkgNameString);
                });

                break;
            default:
                break;
        }
    }

    @Override
    public void onInterrupt() {

    }

    /**
     * Android unbinds this service when the user takes the permission away. The last package it
     * saw is kept in shared storage, which outlives the process, so leaving it there would mean
     * the "do not freeze the foreground application" guard went on refusing to freeze whatever
     * happened to be in front at that moment — for good, with nothing left to update it.
     */
    @Override
    public boolean onUnbind(android.content.Intent intent) {
        MainApplication.clearCurrentPackage();
        return super.onUnbind(intent);
    }

    /**
     * Handles transitions when an application is brought to the foreground.
     *
     * <p>Cancels any pending delayed open-app tasks for {@code previousPkg} (keyed with prefix {@code OSA_})
     * to prevent stale executions, then evaluates and executes matching {@code onApplicationsForeground}
     * automation rules from the scheduled trigger database.
     */
    private void onApplicationsForeground(String previousPkg, String pkgNameString) {

        if (!pkgNameString.equals(previousPkg) && !getPackageName().equals(previousPkg)) {
            cancelAllUnexecutedDelayTasks(this, "OSA_" + previousPkg); // Cancel pending open-app delayed tasks
            SQLiteDatabase db = null;
            Cursor cursor = null;
            try {
                db = openOrCreateDatabase("scheduledTriggerTasks", MODE_PRIVATE, null);
                cursor = db.query("tasks", null, null, null, null, null, null);
                if (cursor != null && cursor.moveToFirst()) {
                    for (int i = 0; i < cursor.getCount(); i++) {
                        String tgExtra = cursor.getString(cursor.getColumnIndex("tgextra"));
                        if (tgExtra == null) {
                            tgExtra = "";
                        }
                        String tg = cursor.getString(cursor.getColumnIndex("tg"));
                        int enabled = cursor.getInt(cursor.getColumnIndex("enabled"));
                        if (enabled == 1 && "onApplicationsForeground".equals(tg) && ("".equals(tgExtra) || Arrays.asList(OneKeyListUtils.decodeUserListsInPackageNames(this, tgExtra.split(","))).contains(pkgNameString))) {
                            String task = cursor.getString(cursor.getColumnIndex("task"));
                            if (task != null && !"".equals(task)) {
                                TasksUtils.runTask(
                                        task.replace("[ppkgn]", previousPkg).replace("[cpkgn]", pkgNameString),
                                        this,
                                        ("".equals(tgExtra) ? null : "OSA_" + pkgNameString)
                                );
                            }
                        }
                        cursor.moveToNext();
                    }
                }
            } catch (SQLiteException e) {
                e.printStackTrace();
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
                if (db != null) {
                    db.close();
                }
            }
        }
    }

    /**
     * Handles transitions when the user navigates away from an application.
     *
     * <p>Checks if an update or package installation was deferred pending the user exiting this app,
     * cancels stale pending leave-app delayed tasks (keyed with prefix {@code OLA_}), and executes
     * registered {@code onLeaveApplications} automation rules.
     */
    private void onLeaveApplications(String previousPkg, String pkgNameString) {

        checkAndInstallWaitingForLeavingToInstallApplication(previousPkg);

        if (!pkgNameString.equals(previousPkg) && !getPackageName().equals(previousPkg)) {
            cancelAllUnexecutedDelayTasks(this, "OLA_" + pkgNameString); // Cancel pending leave-app delayed tasks
            SQLiteDatabase db = null;
            Cursor cursor = null;
            try {
                db = openOrCreateDatabase("scheduledTriggerTasks", MODE_PRIVATE, null);
                cursor = db.query("tasks", null, null, null, null, null, null);
                if (cursor != null && cursor.moveToFirst()) {
                    for (int i = 0; i < cursor.getCount(); i++) {
                        String tg = cursor.getString(cursor.getColumnIndex("tg"));
                        int enabled = cursor.getInt(cursor.getColumnIndex("enabled"));
                        String tgExtra = cursor.getString(cursor.getColumnIndex("tgextra"));
                        if (tgExtra == null) {
                            tgExtra = "";
                        }
                        if (enabled == 1 && "onLeaveApplications".equals(tg) && ("".equals(tgExtra) || Arrays.asList(OneKeyListUtils.decodeUserListsInPackageNames(this, tgExtra.split(","))).contains(previousPkg))) {
                            String task = cursor.getString(cursor.getColumnIndex("task"));
                            if (task != null && !"".equals(task)) {
                                TasksUtils.runTask(
                                        task.replace("[ppkgn]", previousPkg).replace("[cpkgn]", pkgNameString),
                                        this,
                                        "".equals(tgExtra) ? null : "OLA_" + previousPkg
                                );
                            }
                        }
                        cursor.moveToNext();
                    }
                }
            } catch (SQLiteException e) {
                e.printStackTrace();
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
                if (db != null) {
                    db.close();
                }
            }
        }
    }

    private void addUpUseTimes(String currentPackage) {
        DataStatisticsUtils.addUseTimes(getApplicationContext(), currentPackage);
    }

    /**
     * Whether the user asked for this application to be locked when it opens.
     *
     * Read from the same store the settings screen writes, on the thread the events arrive: a
     * package that is not on any list costs one mapped read, which is what lets this run for every
     * window change without a device noticing it.
     */
    private boolean isOnAppLockList(String pkgNameString) {
        return existsInOneKeyList(
                getApplicationContext(), getString(R.string.sAppLockList), pkgNameString);
    }

    /**
     * The gate an application on the protected list still passes through before a lock screen is
     * thrown over it: the app lock has to be switched on, there has to be something to ask with
     * (the phone's own lock or the code built into FreezeYou), and the application must not already
     * be open for this session.
     *
     * The last of the three is what keeps the screen from coming back over the application the
     * user has just proved themselves inside - and, with it, the service from answering its own
     * lock screen with another one.
     *
     * Kept over plain booleans, so the order it refuses in can be read without a device.
     */
    public static boolean appLockShouldIntercept(
            boolean appLockEnabled, boolean authenticatorAvailable, boolean unlockedForSession) {
        return appLockEnabled && authenticatorAvailable && !unlockedForSession;
    }

    /**
     * Whether the way out of an application the lock was raised over still has to be handled.
     *
     * An application the user has been let into is not owed anything here: the window change that
     * reports them inside it names this application's lock as what they left, and the real way out of
     * it arrives later as an ordinary departure, with its freeze-on-quit and its exit rules intact.
     * Any other application behind a lock screen that has stopped being the window in front was
     * refused, and stays open for whoever next looks at the recents screen unless someone pays its
     * leave now.
     *
     * Kept over plain values, like the gate above, so the order it refuses in can be read without a
     * device.
     */
    public static boolean pendingLockOwesDeparture(String pendingLockPkg, boolean unlockedForSession) {
        return pendingLockPkg != null && !"".equals(pendingLockPkg) && !unlockedForSession;
    }

    /**
     * Whether an incoming window change represents returning from an active application lock screen.
     *
     * <p>When an interception lock was standing in front of an application and steps down, the
     * window beneath it briefly surfaces. If the user successfully authenticated, they are entering
     * the application; if they cancelled or backed out, they are refusing entry and must be sent home
     * without re-triggering the lock in a loop.
     */
    /**
     * Identifies whether a window change event represents entering a launcher (home screen)
     * or the system recents / multitasking overview.
     *
     * <p>On many Android variants and OEM skins (e.g. MIUI/HyperOS, Samsung OneUI, Pixel Quickstep),
     * the overview screen is either rendered within the launcher process, hosted under [com.android.systemui],
     * or reported with [AccessibilityEvent#isFullScreen] as false. Treating overview/launcher transitions
     * as genuine departures is essential for re-locking protected applications immediately upon exit.
     */
    /**
     * Determines whether an incoming window change represents a transient input overlay
     * (soft keyboard or OEM volume panel) that should not count as leaving the foreground app.
     */
    public static boolean isTransientInputOverlay(Context context, String pkgNameString, String classNameString) {
        if (classNameString != null) {
            if ("android.inputmethodservice.SoftInputWindow".equals(classNameString)
                    || classNameString.contains("MiuiVolumeDialogImpl")) {
                return true;
            }
        }
        if (context != null && pkgNameString != null) {
            try {
                InputMethodManager imm =
                        (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    List<InputMethodInfo> enabledImes = imm.getEnabledInputMethodList();
                    if (enabledImes != null) {
                        for (InputMethodInfo info : enabledImes) {
                            if (pkgNameString.equals(info.getPackageName())) {
                                return true;
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        return false;
    }

    /**
     * Determines whether an incoming window change represents a system runtime permission request.
     */
    public static boolean isPermissionDialog(String pkgNameString) {
        return "com.android.permissioncontroller".equals(pkgNameString)
                || "com.google.android.permissioncontroller".equals(pkgNameString)
                || "com.android.packageinstaller".equals(pkgNameString);
    }

    /**
     * Identifies transient System UI overlay surfaces (status bar shade, notifications)
     * as opposed to full application switches or launcher / recents multitasking views.
     */
    public static boolean isSystemUiOverlay(Context context, String pkgNameString, String classNameString) {
        if (pkgNameString == null || "".equals(pkgNameString)) {
            return false;
        }
        if (isLauncherOrRecents(context, pkgNameString, classNameString)) {
            return false;
        }
        return "android".equals(pkgNameString)
                || "com.android.systemui".equals(pkgNameString)
                || "miui.systemui.plugin".equals(pkgNameString);
    }

    public static boolean isLauncherOrRecents(Context context, String pkgNameString, String classNameString) {
        if (pkgNameString == null || "".equals(pkgNameString)) {
            return false;
        }

        if (classNameString != null) {
            String lowerClass = classNameString.toLowerCase(Locale.ROOT);
            if (lowerClass.contains("recents")
                    || lowerClass.contains("overview")
                    || lowerClass.contains("taskswitcher")
                    || lowerClass.contains("taskstack")
                    || lowerClass.contains("fsgesture")) {
                return true;
            }
        }

        if ("com.android.launcher3".equals(pkgNameString)
                || "com.google.android.apps.nexuslauncher".equals(pkgNameString)
                || "com.miui.home".equals(pkgNameString)
                || "com.sec.android.app.launcher".equals(pkgNameString)
                || "com.huawei.android.launcher".equals(pkgNameString)
                || "com.oppo.launcher".equals(pkgNameString)
                || "com.oneplus.launcher".equals(pkgNameString)
                || "com.motorola.launcher3".equals(pkgNameString)
                || "com.teslacoilsw.launcher".equals(pkgNameString)
                || "app.lawnchair".equals(pkgNameString)
                || "ch.deletescape.lawnchair".equals(pkgNameString)) {
            return true;
        }

        if (context != null) {
            try {
                PackageManager pm = context.getPackageManager();
                if (pm != null) {
                    Intent homeIntent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
                    ResolveInfo resolveInfo = pm.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY);
                    if (resolveInfo != null && resolveInfo.activityInfo != null) {
                        return pkgNameString.equals(resolveInfo.activityInfo.packageName);
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        return false;
    }

    /**
     * Determines whether an in-app window transition (where previous and current package match)
     * can safely bypass application lock verification.
     *
     * <p>Normal applications or protected applications already authenticated for this session
     * can transition freely between activities. If a protected application is currently locked
     * (e.g. after process termination or unhandled departure), the transition must fall through
     * to raise the lock screen rather than allowing unauthenticated access.
     */
    public static boolean shouldSkipSamePackageTransition(
            boolean isProtectedApp, boolean isUnlocked, boolean shouldIntercept) {
        if (!isProtectedApp) return true;
        if (isUnlocked) return true;
        return !shouldIntercept;
    }

    public static boolean isReturningFromAppLock(
            boolean appLockIsUp, String previousPkg, String currentPkg, String pendingLockPkg, String ourPkg) {
        if (appLockIsUp) return true;
        return ourPkg != null && ourPkg.equals(previousPkg)
                && pendingLockPkg != null && pendingLockPkg.equals(currentPkg);
    }

    /**
     * Determines whether an application that the user was let into should be re-locked immediately
     * upon navigating away from it, based on the configured [appLockReLockPolicy].
     *
     * <p>When set to {@code "immediately"} (or unspecified), the application is locked the instant
     * the user leaves it. When set to {@code "screen_off"} or a millisecond timeout, the departure
     * timestamp is evaluated on re-entry or screen-off instead.
     */
    public static boolean shouldRelockOnAppExit(String reLockPolicy) {
        return reLockPolicy == null || "immediately".equals(reLockPolicy);
    }

    /**
     * Evaluates whether an unlocked application session has expired based on the configured [appLockReLockPolicy],
     * its departure timestamp, and the current system uptime.
     *
     * <p>Policies:
     * <ul>
     *   <li>{@code "immediately"}: always expired upon leaving the application.</li>
     *   <li>{@code "screen_off"}: never expires while the screen remains illuminated.</li>
     *   <li>numeric millisecond string (e.g. {@code "60000"}): expires once the time elapsed since
     *       departure reaches or exceeds the threshold.</li>
     * </ul>
     */
    public static boolean isAppLockSessionExpired(
            String reLockPolicy, Long departureTime, long currentTime) {
        if (reLockPolicy == null || "immediately".equals(reLockPolicy)) {
            return true;
        }
        if ("screen_off".equals(reLockPolicy)) {
            return false;
        }
        try {
            long timeoutMs = Long.parseLong(reLockPolicy);
            if (departureTime == null) {
                return false;
            }
            return (currentTime - departureTime) >= timeoutMs;
        } catch (NumberFormatException e) {
            return true;
        }
    }

    /**
     * Freezes an application the user was stopped at and never entered, when they asked for exactly
     * that of the applications they leave.
     */
    private void freezePendingLockQuit(String pendingLockPkg) {
        if (freezeOnceQuit.getValue(null)
                && existsInOneKeyList(
                getApplicationContext(), getString(R.string.sFreezeOnceQuit), pendingLockPkg)) {
            processFreezeAction(
                    getApplicationContext(), pendingLockPkg, null, null,
                    false, null, false
            );
        }
    }

    /**
     * The lock screen as the interception needs it: raised without an animation over the
     * application that has just taken the screen, asking whether the user may be there at all
     * rather than whether this application is locked, and stamped with the session it was raised
     * in.
     *
     * <p>The stamp is what the screen answers with. A biometric prompt outlives the window that
     * opened it, so a success can arrive after a screen-off ended the session the question was
     * asked in - and an application unlocked into a session nobody is in any more is open in front
     * of whoever next picks the phone up.
     */
    public static Intent buildAppLockIntent(Context context, String pkgNameString) {
        return new Intent(context, AppLockActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_NO_ANIMATION)
                .putExtra("unlockLogoPkgName", pkgNameString)
                .putExtra("targetPkgName", pkgNameString)
                .putExtra("appLockSessionEpoch", ProcessSharedState.getSessionEpoch())
                .putExtra("ignoreCurrentUnlockStatus", true);
    }

    /**
     * Raises the lock screen over the application the user has just entered, and if it cannot be
     * raised, takes the user away from that application.
     *
     * A lock that failed to start is not a lock, and the service has already written this package as
     * the window in front: every later event for it reads as no change, so nothing would ask again
     * and the application the screen was meant to cover would go on open in front of whoever is
     * next to hold the phone. So the recorded package goes back to what it was - which lets the next
     * window change try once more - and the way out is the home screen, the same one a refusal on the
     * lock screen takes. The service is not lost over it: an application nobody could cover is
     * reported here rather than left looking like it was.
     *
     * @return whether the screen is now standing in front of that application.
     */
    private boolean showAppLock(String pkgNameString, String previousPkg) {
        try {
            startActivity(buildAppLockIntent(this, pkgNameString));
            return true;
        } catch (RuntimeException e) {
            Log.w("FreezeYouDebug",
                    "AccessibilityService: app lock could not be raised over " + pkgNameString
                            + ": " + e.getMessage(), e);
            MainApplication.setCurrentPackage(previousPkg);
            leaveTowardsHome();
            return false;
        }
    }

    /**
     * The home screen is the only window that can stand in front of an application nobody has been
     * let into: it is a window of its own rather than one this service would have to close.
     */
    private void leaveTowardsHome() {
        if (performGlobalAction(GLOBAL_ACTION_HOME)) {
            return;
        }
        try {
            startActivity(new Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (RuntimeException e) {
            // No home screen to go to leaves the application visible, which is what the retry on the
            // next window change is for; losing the service over it would leave nothing to retry.
            Log.w("FreezeYouDebug",
                    "AccessibilityService: the home screen could not be reached: " + e.getMessage(), e);
        }
    }

    private void checkAndInstallWaitingForLeavingToInstallApplication(String previousPkg) {
        // The persisted record is the only copy, so process death cannot drop the request.
        final InstallPackagesUtils.WaitingInstallRequest request =
                InstallPackagesUtils.getWaitingForLeavingRequest();
        if (request == null || !previousPkg.equals(request.packageName)) {
            return;
        }
        if (!request.isApkAvailable()) {
            discardWaitingInstall(request);
            return;
        }
        dispatchWaitingInstall(request);
    }

    private void dispatchWaitingInstall(InstallPackagesUtils.WaitingInstallRequest request) {
        // Clear record first so any concurrent replacement knows this request was already dispatched
        InstallPackagesUtils.clearWaitingForLeavingRequestIfMatches(request);
        try {
            ServiceUtils.startService(this, request.toIntent(this));
        } catch (RuntimeException e) {
            // Restore request if service start failed
            InstallPackagesUtils.saveWaitingForLeavingRequest(this, request.toIntent(this), request.packageName);
            Log.w("FreezeYouDebug",
                    "AccessibilityService: waiting install start failed: " + e.getMessage(), e);
        }
    }

    private void discardWaitingInstall(InstallPackagesUtils.WaitingInstallRequest request) {
        if (!InstallPackagesUtils.discardWaitingForLeavingRequest(this, request)) return;
        if (request.apkFilePath != null) {
            InstallPackagesUtils.deleteTempFile(this, request.apkFilePath, false);
        }
    }
}
