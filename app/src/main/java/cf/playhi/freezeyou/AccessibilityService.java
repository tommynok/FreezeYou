package cf.playhi.freezeyou;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.res.Configuration;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.os.Build;
import android.os.Parcelable;
import android.os.PowerManager;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

import androidx.annotation.CallSuper;

import java.io.File;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import cf.playhi.freezeyou.utils.DataStatisticsUtils;
import cf.playhi.freezeyou.utils.InstallPackagesUtils;
import cf.playhi.freezeyou.utils.OneKeyListUtils;
import cf.playhi.freezeyou.utils.ServiceUtils;
import cf.playhi.freezeyou.utils.ProcessSharedState;
import cf.playhi.freezeyou.utils.TasksUtils;

import static cf.playhi.freezeyou.storage.key.DefaultMultiProcessMMKVStorageBooleanKeys.freezeOnceQuit;
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
    }

    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();

    @Override
    public void onDestroy() {
        mExecutor.shutdown();
        super.onDestroy();
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
     *   <li>If the user switched to a different package, updates [MainApplication#getCurrentPackage] and
     *       dispatches exit triggers ([onLeaveApplications]) and entry triggers ([onApplicationsForeground]).</li>
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

    private void checkAndInstallWaitingForLeavingToInstallApplication(String previousPkg) {
        // The persisted record is the source of truth: it is the copy that survives the process,
        // and reading it first keeps one leave event from dispatching the request twice when the
        // in-memory copy is still around as well.
        final InstallPackagesUtils.WaitingInstallRequest persisted =
                InstallPackagesUtils.getWaitingForLeavingRequest();
        if (persisted != null && previousPkg.equals(persisted.packageName)) {
            if (!persisted.isApkAvailable()) {
                discardWaitingInstall(previousPkg, persisted.apkFilePath);
                return;
            }
            dispatchWaitingInstall(persisted.toIntent(this));
            return;
        }

        final Intent intent = MainApplication.getWaitingForLeavingToInstallApplicationIntent();
        if (intent == null || !previousPkg.equals(getWaitingInstallTargetPackage(intent))) {
            return;
        }
        final String apkFilePath = intent.getStringExtra("apkFilePath");
        if (!isWaitingInstallApkAvailable(
                intent.getBooleanExtra("install", true), apkFilePath)) {
            discardWaitingInstall(previousPkg, apkFilePath);
            return;
        }
        dispatchWaitingInstall(intent);
    }

    private static String getWaitingInstallTargetPackage(Intent intent) {
        if (intent == null) return null;
        final Parcelable packageInfoParcelable = intent.getParcelableExtra("packageInfo");
        final PackageInfo packageInfo =
                packageInfoParcelable instanceof PackageInfo
                        ? (PackageInfo) packageInfoParcelable : null;
        return packageInfo == null ? null : packageInfo.packageName;
    }

    private static boolean isWaitingInstallApkAvailable(boolean install, String apkFilePath) {
        return !install || apkFilePath == null || apkFilePath.isEmpty()
                || new File(apkFilePath).exists();
    }

    private void dispatchWaitingInstall(Intent intent) {
        // The wait is over once the user has left; dropping the flag keeps the service from
        // putting the request back into the waiting slot if the target is in front again by the
        // time this worker runs. The install path re-reads everything from the APK anyway.
        intent.removeExtra("waitForLeaving");
        intent.removeExtra("packageInfo");
        try {
            ServiceUtils.startService(this, intent);
        } catch (RuntimeException e) {
            // Keep the request and its waiting card: the next leave event can try again.
            Log.w("FreezeYouDebug",
                    "AccessibilityService: waiting install start failed: " + e.getMessage(), e);
            return;
        }
        // Cleared only after the service accepted the request, so a failed start cannot burn it.
        // Both this callback and the service run on the main thread, so nothing can save a
        // newer request between the successful start and this clear.
        MainApplication.setWaitingForLeavingToInstallApplicationIntent(null);
        InstallPackagesUtils.clearWaitingForLeavingRequest();
    }

    private void discardWaitingInstall(String targetPackage, String apkFilePath) {
        MainApplication.setWaitingForLeavingToInstallApplicationIntent(null);
        InstallPackagesUtils.clearWaitingForLeavingRequest();
        InstallPackagesUtils.cancelWaitingForLeavingToInstallApplicationNotification(
                this, targetPackage);
        if (apkFilePath != null) {
            InstallPackagesUtils.deleteTempFile(this, apkFilePath, false);
        }
    }
}
