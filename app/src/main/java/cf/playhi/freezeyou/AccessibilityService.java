package cf.playhi.freezeyou;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.res.Configuration;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;
import android.os.Parcelable;
import android.os.PowerManager;
import android.view.accessibility.AccessibilityEvent;

import androidx.annotation.CallSuper;

import java.util.Arrays;
import java.util.Locale;

import cf.playhi.freezeyou.utils.DataStatisticsUtils;
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

// Needs to be retained for compatibility
// with old FreezeYou structures and settings.
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

    @Override
    public void onAccessibilityEvent(AccessibilityEvent accessibilityEvent) {
        int type = accessibilityEvent.getEventType();
        switch (type) {
            case AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED:

                if (!accessibilityEvent.isFullScreen()) return;

                CharSequence pkgName = accessibilityEvent.getPackageName();
                if (pkgName == null) return;
                String pkgNameString = pkgName.toString();

                addUpUseTimes(pkgNameString);

                if ("".equals(pkgNameString)
                        || "android".equals(pkgNameString)
                        || "com.android.systemui".equals(pkgNameString)
                        || "com.android.packageinstaller".equals(pkgNameString) // Grant permissions, etc.
                        || "miui.systemui.plugin".equals(pkgNameString) // MIUI System UI plugin (popups, volume dialogs, etc.)

                ) {
                    return;
                }

                String className = accessibilityEvent.getClassName().toString();
                if ("android.inputmethodservice.SoftInputWindow".equals(className)
                        || "com.miui.misound.playervolume.MiuiVolumeDialogImpl$CustomDialog".equals(className) // MIUI volume panel
                ) {
                    return;
                }

                PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
                if (pm == null) return;
                if (!pm.isScreenOn()) return;

                String previousPkg = MainApplication.getCurrentPackage();

                MainApplication.setCurrentPackage(pkgNameString);
                if (!pkgNameString.equals(previousPkg)
                        && freezeOnceQuit.getValue(null)
                        && existsInOneKeyList(getApplicationContext(), getString(R.string.sFreezeOnceQuit), previousPkg)) {
                    processFreezeAction(
                            getApplicationContext(), previousPkg, null, null,
                            false, null, false
                    );
                }

                onLeaveApplications(previousPkg, pkgNameString);

                onApplicationsForeground(previousPkg, pkgNameString);

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
        ProcessSharedState.clearForegroundPackage();
        return super.onUnbind(intent);
    }

    private void onApplicationsForeground(String previousPkg, String pkgNameString) {

        if (!pkgNameString.equals(previousPkg) && !"cf.playhi.freezeyou".equals(previousPkg)) {
            cancelAllUnexecutedDelayTasks(this, "OSA_" + previousPkg); // Cancel pending open-app delayed tasks
            final SQLiteDatabase db = openOrCreateDatabase("scheduledTriggerTasks", MODE_PRIVATE, null);
            db.execSQL(
                    "create table if not exists tasks(_id integer primary key autoincrement,tg varchar,tgextra varchar,enabled integer(1),label varchar,task varchar,column1 varchar,column2 varchar)"
            );
            Cursor cursor = db.query("tasks", null, null, null, null, null, null);
            if (cursor.moveToFirst()) {
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
            cursor.close();
            db.close();
        }
    }

    private void onLeaveApplications(String previousPkg, String pkgNameString) {

        checkAndInstallWaitingForLeavingToInstallApplication(previousPkg);

        if (!pkgNameString.equals(previousPkg) && !"cf.playhi.freezeyou".equals(previousPkg)) {
            cancelAllUnexecutedDelayTasks(this, "OLA_" + pkgNameString); // Cancel pending leave-app delayed tasks
            final SQLiteDatabase db = openOrCreateDatabase("scheduledTriggerTasks", MODE_PRIVATE, null);
            db.execSQL(
                    "create table if not exists tasks(_id integer primary key autoincrement,tg varchar,tgextra varchar,enabled integer(1),label varchar,task varchar,column1 varchar,column2 varchar)"
            );
            Cursor cursor = db.query("tasks", null, null, null, null, null, null);
            if (cursor.moveToFirst()) {
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
            cursor.close();
            db.close();
        }
    }

    private void addUpUseTimes(String currentPackage) {
        DataStatisticsUtils.addUseTimes(getApplicationContext(), currentPackage);
    }

    private void checkAndInstallWaitingForLeavingToInstallApplication(String previousPkg) {
        final Intent intent = MainApplication.getWaitingForLeavingToInstallApplicationIntent();
        if (intent == null) return;
        final Parcelable packageInfoParcelable = intent.getParcelableExtra("packageInfo");
        final PackageInfo packageInfo =
                packageInfoParcelable instanceof PackageInfo
                        ? (PackageInfo) packageInfoParcelable : null;
        if (packageInfo != null && previousPkg.equals(packageInfo.packageName)) {
            ServiceUtils.startService(AccessibilityService.this, intent);
            MainApplication.setWaitingForLeavingToInstallApplicationIntent(null);
        }
    }
}
