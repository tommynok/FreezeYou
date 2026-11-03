package cf.playhi.freezeyou;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.os.Build;
import android.os.PowerManager;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

import androidx.annotation.CallSuper;

import java.util.Arrays;
import java.util.Locale;
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
import static cf.playhi.freezeyou.utils.AuthenticationUtils.isBiometricPromptPartAvailable;
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

                if (!accessibilityEvent.isFullScreen()) return;

                CharSequence pkgName = accessibilityEvent.getPackageName();
                if (pkgName == null) return;
                String pkgNameString = pkgName.toString();

                if ("".equals(pkgNameString)
                        || "android".equals(pkgNameString)
                        || "com.android.systemui".equals(pkgNameString)
                        || "com.android.packageinstaller".equals(pkgNameString) // Grant permissions, etc.
                        || "miui.systemui.plugin".equals(pkgNameString) // MIUI System UI plugin (popups, volume dialogs, etc.)
                ) {
                    return;
                }

                CharSequence className = accessibilityEvent.getClassName();
                if (className != null) {
                    String classNameString = className.toString();
                    if ("android.inputmethodservice.SoftInputWindow".equals(classNameString)
                            || "com.miui.misound.playervolume.MiuiVolumeDialogImpl$CustomDialog".equals(classNameString) // MIUI volume panel
                    ) {
                        return;
                    }
                }

                PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
                if (pm == null) return;
                if (!pm.isScreenOn()) return;

                String previousPkg = MainApplication.getCurrentPackage();

                if (pkgNameString.equals(previousPkg)) {
                    return;
                }

                MainApplication.setCurrentPackage(pkgNameString);

                if (getPackageName().equals(pkgNameString)) {
                    // The window of this application is the lock screen raised over an application
                    // the user has not been let into yet. They have not left that application - the
                    // screen in front of them belongs to this one, and answering it as a departure
                    // would freeze it or run its exit tasks while the question is still open.
                    if (mAppLockIsUp) return;
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
                    if (shouldRelockOnAppExit(appLockReLockPolicy.getValue(null))) {
                        ProcessSharedState.clearPackageUnlocked(previousPkg);
                    }

                    if (isOnAppLockList(pkgNameString) && appLockShouldIntercept(
                            isAppLockEnabled(), isBiometricPromptPartAvailable(this),
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
                    }
                }

                addUpUseTimes(pkgNameString);

                if (freezeOnceQuit.getValue(null)
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
     * thrown over it: the app lock has to be switched on, the device has to be able to ask for
     * it, and the application must not already be open for this session.
     *
     * The last of the three is what keeps the screen from coming back over the application the
     * user has just proved themselves inside - and, with it, the service from answering its own
     * lock screen with another one.
     *
     * Kept over plain booleans, so the order it refuses in can be read without a device.
     */
    public static boolean appLockShouldIntercept(
            boolean appLockEnabled, boolean promptAvailable, boolean unlockedForSession) {
        return appLockEnabled && promptAvailable && !unlockedForSession;
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
     * Determines whether an application that the user was let into should be re-locked immediately
     * upon navigating away from it, based on the configured [appLockReLockPolicy].
     *
     * <p>When set to "screen_off", the application remains unlocked while the screen is on, and is
     * re-locked only when the screen goes dark or the session is reset.
     */
    public static boolean shouldRelockOnAppExit(String reLockPolicy) {
        return !"screen_off".equals(reLockPolicy);
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
