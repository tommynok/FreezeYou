package cf.playhi.freezeyou.receiver;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.provider.Settings;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import cf.playhi.freezeyou.MyNotificationListenerService;
import cf.playhi.freezeyou.service.ScreenLockOneKeyFreezeService;
import cf.playhi.freezeyou.utils.AccessibilityUtils;
import cf.playhi.freezeyou.utils.FileUtils;
import cf.playhi.freezeyou.utils.NotificationUtils;
import cf.playhi.freezeyou.utils.PackageListUtils;
import cf.playhi.freezeyou.utils.ProcessSharedState;
import cf.playhi.freezeyou.utils.ServiceUtils;
import cf.playhi.freezeyou.utils.TasksUtils;
import cf.playhi.freezeyou.utils.VersionUtils;

import static cf.playhi.freezeyou.storage.key.DefaultMultiProcessMMKVStorageBooleanKeys.combineBatchQuickNotification;
import static cf.playhi.freezeyou.storage.key.DefaultMultiProcessMMKVStorageBooleanKeys.createQuickFUFNotiAfterUnfrozen;
import static cf.playhi.freezeyou.storage.key.DefaultMultiProcessMMKVStorageBooleanKeys.onekeyFreezeWhenLockScreen;
import static cf.playhi.freezeyou.utils.FUFUtils.checkAndCreateFUFQuickNotification;
import static cf.playhi.freezeyou.utils.FUFUtils.checkMRootFrozen;
import static cf.playhi.freezeyou.utils.FUFUtils.checkRootFrozen;

public class BootCompletedReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {

        if (intent == null || intent.getAction() == null) {
            return;
        }

        final PendingResult pendingResult = goAsync();
        final String action = intent.getAction();
        new Thread(() -> {
            try {
                switch (action) {
                    case Intent.ACTION_BOOT_COMPLETED:
                        // A reboot runs neither watcher's teardown, so what it last saw is still in
                        // MMKV. What needs taking out is the state left by a watcher the user has
                        // since switched off, because nothing will ever overwrite it. A watcher that
                        // stays on publishes again on its own next event, and clearing that snapshot
                        // could erase one this boot already produced, which would let a freeze
                        // through against an application that is notifying or in the foreground.
                        if (!AccessibilityUtils.isAccessibilitySettingsOn(context)) {
                            ProcessSharedState.clearForegroundPackage();
                        }
                        if (!isNotificationListenerEnabled(context)) {
                            ProcessSharedState.setNotifyingPackages(Collections.emptyList());
                        }
                        runBackgroundService(context);
                        checkAndReNotifyNotifications(context);
                        checkTasks(context);
                        break;
                    case Intent.ACTION_MY_PACKAGE_REPLACED:
                        runBackgroundService(context);
                        checkAndReNotifyNotifications(context);
                        checkTasks(context);
                        cleanExternalCache(context);

                        final SharedPreferences sharedPreferences =
                                context.getSharedPreferences("Ver", Context.MODE_PRIVATE);
                        if (sharedPreferences.getInt("Ver", 0) < VersionUtils.getVersionCode(context)) {
                            clearCrashLogs(context);
                        }

                        break;
                    default:
                        break;
                }
            } catch (Throwable t) {
                t.printStackTrace();
            } finally {
                pendingResult.finish();
            }
        }, "BootCompletedWorker").start();
    }

    private void cleanExternalCache(Context context) {
        try {
            FileUtils.deleteAllFiles(context.getExternalCacheDir(), false);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void runBackgroundService(Context context) {
        if (onekeyFreezeWhenLockScreen.getValue(null)) {
            ServiceUtils.startService(context,
                    new Intent(context, ScreenLockOneKeyFreezeService.class));
        }
    }

    private void checkAndReNotifyNotifications(Context context) {
        reNotifyUnfrozen(context, NotificationUtils.getNotifyingPackages());

        // The list sat in the default SharedPreferences before it sat in Tray. An install that has
        // not run a build since the move still holds it there, so it is re-notified once and dropped.
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        List<String> legacy = Arrays.asList(
                PackageListUtils.parse(sharedPreferences.getString("notifying", "")));
        if (!legacy.isEmpty()) {
            reNotifyUnfrozen(context, legacy);
            sharedPreferences.edit().remove("notifying").apply();
        }
    }

    private void reNotifyUnfrozen(Context context, List<String> pkgNames) {
        // With quick notifications off nothing below is posted, so the stored list is all that is
        // left of them, and the manage screen keeps naming applications that have no card. The
        // entries are taken out rather than the cards cancelled: the system removed the cards with
        // the reboot or the update that led here.
        if (!createQuickFUFNotiAfterUnfrozen.getValue(null)) {
            NotificationUtils.deleteNotifying(context, pkgNames);
            return;
        }
        PackageManager pm = context.getPackageManager();
        List<String> stillUnfrozen = new ArrayList<>();
        List<String> alreadyFrozen = new ArrayList<>();
        for (String aPkgName : pkgNames) {
            if (checkFrozenStatus(context, aPkgName, pm)) {
                alreadyFrozen.add(aPkgName);
            } else {
                stillUnfrozen.add(aPkgName);
            }
        }
        // Something froze the app while the phone was off, so its name is still on a list of
        // notifications that no longer exist. Leaving it there keeps the count on the card wrong from
        // the first screen after boot, and one pass takes the whole lot out at once.
        if (!alreadyFrozen.isEmpty()) {
            NotificationUtils.deleteNotifying(context, alreadyFrozen);
        }
        // One notification per application, as they were put out in the first place. The single card
        // for the whole lot is the user's own choice; it is also what keeps a boot that unfroze
        // twenty applications from leaving twenty rows on a screen nobody is looking at.
        if (combineBatchQuickNotification.getValue(null) && stillUnfrozen.size() > 1) {
            NotificationUtils.createBatchQuickNotification(context, stillUnfrozen);
        } else {
            for (String aPkgName : stillUnfrozen) {
                checkAndCreateFUFQuickNotification(context, aPkgName);
            }
        }
    }

    private boolean checkFrozenStatus(Context context, String packageName, PackageManager pm) {
        return (checkRootFrozen(context, packageName, pm) || checkMRootFrozen(context, packageName));
    }

    /**
     * Below API 21 nothing asks whether an application is notifying, so there is no guard to correct
     * and the stored set is left alone. Above it the setting is a colon-separated list of components,
     * and the entry is matched whole: a substring would find another listener whose component names
     * this package and leave a set nobody can overwrite. No value at all is the same case as a
     * listener switched off - there is nothing that could publish - so it asks for the clear.
     */
    private boolean isNotificationListenerEnabled(Context context) {
        if (Build.VERSION.SDK_INT < 21) {
            return true;
        }
        String enabledListeners = Settings.Secure.getString(
                context.getContentResolver(), "enabled_notification_listeners");
        if (enabledListeners == null || enabledListeners.isEmpty()) {
            return false;
        }
        for (String entry : enabledListeners.split(":")) {
            ComponentName listener = ComponentName.unflattenFromString(entry);
            if (listener == null || !context.getPackageName().equals(listener.getPackageName())) {
                continue;
            }
            // A flattened component may hold the class without its package prefix.
            String className = listener.getClassName();
            if (className.startsWith(".")) {
                className = context.getPackageName() + className;
            }
            if (MyNotificationListenerService.class.getCanonicalName().equals(className)) {
                return true;
            }
        }
        return false;
    }

    private void checkTasks(Context context) {
        checkTimeTasks(context);
        checkTriggerTasks(context);
    }

    private void checkTimeTasks(Context context) {
        TasksUtils.checkTimeTasks(context);
    }

    private void checkTriggerTasks(Context context) {
        TasksUtils.checkTriggerTasks(context);
    }

    private void clearCrashLogs(Context context) {
        if (Environment.getExternalStorageState().equals(
                Environment.MEDIA_MOUNTED)) {
            String logPath =
                    Environment.getExternalStorageDirectory()
                            .getAbsolutePath()
                            + File.separator
                            + File.separator
                            + "FreezeYou"
                            + File.separator
                            + "Log";
            try {
                FileUtils.deleteAllFiles(new File(logPath), false);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        String logPath2 =
                Environment.getDataDirectory().getPath()
                        + File.separator
                        + "data"
                        + File.separator
                        + context.getPackageName()
                        + File.separator
                        + "log";
        try {
            FileUtils.deleteAllFiles(new File(logPath2), false);
        } catch (IOException e) {
            e.printStackTrace();
        }

        File crashCheck =
                new File(logPath2
                        + File.separator
                        + "NeedUpload.log");
        if (crashCheck.exists()) {
            crashCheck.delete();
        }
    }
}
