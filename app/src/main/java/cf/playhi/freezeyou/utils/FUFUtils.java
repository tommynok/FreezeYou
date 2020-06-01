package cf.playhi.freezeyou.utils;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.service.notification.StatusBarNotification;

import net.grandcentrix.tray.AppPreferences;

import java.io.DataOutputStream;

import cf.playhi.freezeyou.AskRunActivity;
import cf.playhi.freezeyou.DeviceAdminReceiver;
import cf.playhi.freezeyou.FUFService;
import cf.playhi.freezeyou.MainApplication;
import cf.playhi.freezeyou.MyNotificationListenerService;
import cf.playhi.freezeyou.R;
import cf.playhi.freezeyou.fuf.FUFSinglePackage;

import static cf.playhi.freezeyou.fuf.FUFSinglePackage.ACTION_MODE_FREEZE;
import static cf.playhi.freezeyou.fuf.FUFSinglePackage.ACTION_MODE_UNFREEZE;
import static cf.playhi.freezeyou.fuf.FUFSinglePackage.API_FREEZEYOU_MROOT_DPM;
import static cf.playhi.freezeyou.fuf.FUFSinglePackage.API_FREEZEYOU_ROOT_DISABLE_ENABLE;
import static cf.playhi.freezeyou.fuf.FUFSinglePackage.API_FREEZEYOU_ROOT_UNHIDE_HIDE;
import static cf.playhi.freezeyou.fuf.FUFSinglePackage.ERROR_NO_ERROR_SUCCESS;
import static cf.playhi.freezeyou.utils.ApplicationIconUtils.getApplicationIcon;
import static cf.playhi.freezeyou.utils.ApplicationIconUtils.getBitmapFromDrawable;
import static cf.playhi.freezeyou.utils.ApplicationInfoUtils.getApplicationInfoFromPkgName;
import static cf.playhi.freezeyou.utils.DevicePolicyManagerUtils.getDevicePolicyManager;
import static cf.playhi.freezeyou.utils.NotificationUtils.createFUFQuickNotification;
import static cf.playhi.freezeyou.utils.NotificationUtils.deleteNotification;
import static cf.playhi.freezeyou.utils.ProcessUtils.destroyProcess;
import static cf.playhi.freezeyou.utils.ServiceUtils.startService;
import static cf.playhi.freezeyou.utils.TasksUtils.onFApplications;
import static cf.playhi.freezeyou.utils.TasksUtils.onUFApplications;
import static cf.playhi.freezeyou.utils.TasksUtils.runTask;
import static cf.playhi.freezeyou.utils.ToastUtils.showToast;

public final class FUFUtils {

    public static void askRun(final Context context, final String pkgName, String target, String tasks, final boolean runImmediately, Activity activity, boolean finish) {
        if (runImmediately || (new AppPreferences(context).getBoolean("openImmediately", false))) {
            checkAndStartApp(context, pkgName, target, tasks, activity, finish);
        } else {
            if (!context.getString(R.string.onlyUnfreeze).equals(target)) {
                context.startActivity(new Intent(context, AskRunActivity.class)
                        .putExtra("pkgName", pkgName)
                        .putExtra("target", target)
                        .putExtra("tasks", tasks)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            }
        }
    }

    static void checkAndDoActivityFinish(Activity activity, boolean finish) {
        if (activity != null && finish) {
            activity.finish();
        }
    }

    public static boolean processAction(final String pkgName, String target, String tasks, final Context context, final boolean enable, final boolean askRun, boolean runImmediately, Activity activity, boolean finish) {
        return
                processAction(
                        pkgName, target, tasks,
                        context, enable, askRun, runImmediately,
                        activity, finish,
                        new AppPreferences(context).getInt("selectFUFMode", 0)
                );
    }

    public static boolean processAction(final String pkgName, String target, String tasks, final Context context, final boolean enable, final boolean askRun, boolean runImmediately, Activity activity, boolean finish, int apiMode) {
        boolean returnValue = false;

        int result =
                checkAndExecuteAction(
                        context, pkgName,
                        apiMode,
                        enable ?
                                ACTION_MODE_UNFREEZE :
                                ACTION_MODE_FREEZE
                );

        if (result == ERROR_NO_ERROR_SUCCESS) {
            sendStatusChangedBroadcast(context);
            if (enable) {
                onUFApplications(context, pkgName);
                checkAndCreateFUFQuickNotification(context, pkgName);
                if (askRun) {
                    askRun(context, pkgName, target, tasks, runImmediately, activity, finish);
                }
            } else {
                onFApplications(context, pkgName);
                deleteNotification(context, pkgName);
            }
            returnValue = true;
        } else {
            if (apiMode == API_FREEZEYOU_ROOT_DISABLE_ENABLE || apiMode == API_FREEZEYOU_ROOT_UNHIDE_HIDE) {
                showToast(context, R.string.mayUnrootedOrOtherEx);
            }
        }
        return returnValue;
    }

    public static boolean processRootAction(final String pkgName, String target, String tasks, final Context context, final boolean enable, final boolean askRun, boolean runImmediately, Activity activity, boolean finish) {
        return
                processAction(
                        pkgName, target, tasks,
                        context, enable, askRun,
                        runImmediately, activity, finish,
                        API_FREEZEYOU_ROOT_DISABLE_ENABLE
                );
    }

    @TargetApi(21)
    public static boolean processMRootAction(Context context, String pkgName, String target, String tasks, boolean hidden, boolean askRun, boolean runImmediately, Activity activity, boolean finish) {
        return
                processAction(
                        pkgName, target, tasks,
                        context, !hidden, askRun,
                        runImmediately, activity, finish,
                        API_FREEZEYOU_MROOT_DPM
                );
    }

    public static int checkAndExecuteAction(Context context, String pkgName, int apiMode, int actionMode) {
        int returnValue = 999;
        String currentPackage = " ";
        if (new AppPreferences(context).getBoolean("avoidFreezeForegroundApplications", false)) {
            currentPackage = MainApplication.getCurrentPackage();
        }
        if ((!"cf.playhi.freezeyou".equals(pkgName))) {
            if (isAvoidFreezeNotifyingApplicationsEnabledAndAppStillNotifying(context, pkgName)) {
                checkAndShowAppStillNotifyingToast(context, pkgName);
            } else if (pkgName.equals(currentPackage)) {
                checkAndShowAppIsForegroundApplicationToast(context, pkgName);
            } else {
                FUFSinglePackage fufSinglePackage = new FUFSinglePackage(context);
                fufSinglePackage.setSinglePackageName(pkgName);
                fufSinglePackage.setAPIMode(apiMode);
                fufSinglePackage.setActionMode(actionMode);
                returnValue = fufSinglePackage.commit();
            }
        }
        return returnValue;
    }

    public static void checkAndStartApp(Context context, String pkgName, String target, String tasks, Activity activity, boolean finish) {
        if (target != null) {
            if (!context.getString(R.string.onlyUnfreeze).equals(target)) {
                try {
                    ComponentName component = new ComponentName(pkgName, target);
                    Intent intent = new Intent();
                    intent.setComponent(component);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    intent.setAction(Intent.ACTION_MAIN);
                    context.startActivity(intent);
                } catch (SecurityException e) {
                    e.printStackTrace();
                    showToast(context, R.string.insufficientPermission);
                }
            }
        } else if (context.getPackageManager().getLaunchIntentForPackage(pkgName) != null) {
            Intent intent = new Intent(
                    context.getPackageManager().getLaunchIntentForPackage(pkgName));
            context.startActivity(intent);
        } else {
            showToast(context,
                    R.string.unrootedOrCannotFindTheLaunchIntent);
        }

        if (tasks != null) {
            runTask(tasks, context, null);
        }

        checkAndDoActivityFinish(activity, finish);
    }

    public static void oneKeyActionRoot(Context context, boolean freeze, String[] pkgNameList) {
        if (pkgNameList != null) {
            String currentPackage = " ";
            if (new AppPreferences(context).getBoolean("avoidFreezeForegroundApplications", false)) {
                currentPackage = MainApplication.getCurrentPackage();
            }
            Process process = null;
            DataOutputStream outputStream = null;
            try {
                process = Runtime.getRuntime().exec("su");
                outputStream = new DataOutputStream(process.getOutputStream());
                if (freeze) {
                    for (String aPkgNameList : pkgNameList) {
                        if ((!"cf.playhi.freezeyou".equals(aPkgNameList))) {
                            if (isAvoidFreezeNotifyingApplicationsEnabledAndAppStillNotifying(context, aPkgNameList)) {
                                checkAndShowAppStillNotifyingToast(context, aPkgNameList);
                            } else if (currentPackage.equals(aPkgNameList)) {
                                checkAndShowAppIsForegroundApplicationToast(context, aPkgNameList);
                            } else {
                                try {
                                    int tmp = context.getPackageManager().getApplicationEnabledSetting(aPkgNameList);
                                    if (tmp != PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER && tmp != PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
                                        outputStream.writeBytes(
                                                "pm disable " + aPkgNameList + "\n");
                                    }
                                } catch (Exception e) {
                                    e.printStackTrace();
//                                    if (!(new AppPreferences(context).getBoolean("lesserToast", false))) {
//                                        showToast(context, R.string.plsRemoveUninstalledApplications);
//                                    }
                                }
                            }
                        }
                    }
                } else {
                    for (String aPkgNameList : pkgNameList) {
                        try {
                            int tmp = context.getPackageManager().getApplicationEnabledSetting(aPkgNameList);
                            if (tmp == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER || tmp == PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
                                outputStream.writeBytes(
                                        "pm enable " + aPkgNameList + "\n");
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
//                            if (!(new AppPreferences(context).getBoolean("lesserToast", false))) {
//                                showToast(context, R.string.plsRemoveUninstalledApplications);
//                            }
                        }
                    }
                }
                outputStream.writeBytes("exit\n");
                outputStream.flush();
                int exitValue = process.waitFor();
                if (exitValue == 0) {
                    if (freeze) {
                        for (String aPkgNameList : pkgNameList) {
                            onFApplications(context, aPkgNameList);
                            deleteNotification(context, aPkgNameList);
                        }
                    } else {
                        for (String aPkgNameList : pkgNameList) {
                            onUFApplications(context, aPkgNameList);
                            checkAndCreateFUFQuickNotification(context, aPkgNameList);
                        }
                    }
                    if (!(new AppPreferences(context).getBoolean("lesserToast", false))) {
                        showToast(context, R.string.executed);
                    }
                } else {
                    showToast(context, R.string.mayUnrootedOrOtherEx);
                }
                destroyProcess(outputStream, process);
            } catch (Exception e) {
                e.printStackTrace();
                showToast(context, context.getString(R.string.exception) + e.getMessage());
                if (e.getMessage().toLowerCase().contains("permission denied") || e.getMessage().toLowerCase().contains("not found")) {
                    showToast(context, R.string.mayUnrooted);
                }
                destroyProcess(outputStream, process);
            }
            sendStatusChangedBroadcast(context);
        }
    }

    @TargetApi(21)
    public static void oneKeyActionMRoot(Context context, boolean freeze, String[] pkgNameList) {
        if (pkgNameList != null) {
            String currentPackage = " ";
            if (new AppPreferences(context).getBoolean("avoidFreezeForegroundApplications", false)) {
                currentPackage = MainApplication.getCurrentPackage();
            }
            for (String aPkgNameList : pkgNameList) {
                try {
                    if (freeze) {
                        if ((!"cf.playhi.freezeyou".equals(aPkgNameList)) && (!checkMRootFrozen(context, aPkgNameList))) {
                            if (currentPackage.equals(aPkgNameList)) {
                                checkAndShowAppIsForegroundApplicationToast(context, aPkgNameList);
                            } else if (isAvoidFreezeNotifyingApplicationsEnabledAndAppStillNotifying(context, aPkgNameList)) {
                                checkAndShowAppStillNotifyingToast(context, aPkgNameList);
                            } else {
                                if (getDevicePolicyManager(context).setApplicationHidden(
                                        DeviceAdminReceiver.getComponentName(context), aPkgNameList, true)) {
                                    onFApplications(context, aPkgNameList);
                                    deleteNotification(context, aPkgNameList);
                                } else {
                                    showToast(context, aPkgNameList + " " + context.getString(R.string.failed) + " " + context.getString(R.string.mayUnrootedOrOtherEx));
                                }
                            }
                        }
                    } else {
                        if (checkMRootFrozen(context, aPkgNameList)) {
                            if (getDevicePolicyManager(context).setApplicationHidden(
                                    DeviceAdminReceiver.getComponentName(context), aPkgNameList, false)) {
                                onUFApplications(context, aPkgNameList);
                                checkAndCreateFUFQuickNotification(context, aPkgNameList);
                            } else {
                                showToast(context, aPkgNameList + " " + context.getString(R.string.failed) + " " + context.getString(R.string.mayUnrootedOrOtherEx));
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    showToast(context, context.getString(R.string.exceptionHC) + e.getLocalizedMessage());
                }
            }
            sendStatusChangedBroadcast(context);
            if (!(new AppPreferences(context).getBoolean("lesserToast", false))) {
                showToast(context, R.string.executed);
            }
        }
    }

    private static void sendStatusChangedBroadcast(Context context) {
        Intent intent = new Intent();
        intent.setAction("cf.playhi.freezeyou.action.packageStatusChanged");
        intent.setPackage("cf.playhi.freezeyou");
        context.sendBroadcast(intent);
    }

    @TargetApi(21)
    private static boolean isAppStillNotifying(String pkgName) {
        if (pkgName != null) {
            StatusBarNotification[] statusBarNotifications = MyNotificationListenerService.getStatusBarNotifications();
            if (statusBarNotifications != null) {
                for (StatusBarNotification aStatusBarNotifications : statusBarNotifications) {
                    if (pkgName.equals(aStatusBarNotifications.getPackageName())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean isAvoidFreezeNotifyingApplicationsEnabledAndAppStillNotifying(Context context, String pkgName) {
        if (Build.VERSION.SDK_INT >= 21) {
            return new AppPreferences(context).getBoolean("avoidFreezeNotifyingApplications", false) && isAppStillNotifying(pkgName);
        } else {
            return false;
        }
    }

    private static void checkAndShowAppStillNotifyingToast(Context context, String pkgName) {
        String label =
                ApplicationLabelUtils.getApplicationLabel(
                        context,
                        null,
                        null,
                        pkgName);
        if (!context.getString(R.string.uninstalled).equals(label))
            showToast(
                    context,
                    String.format(
                            context.getString(R.string.appHasNotifi),
                            label
                    )
            );
    }

    private static void checkAndShowAppIsForegroundApplicationToast(Context context, String pkgName) {
        String label =
                ApplicationLabelUtils.getApplicationLabel(
                        context,
                        null,
                        null,
                        pkgName);
        if (!context.getString(R.string.uninstalled).equals(label))
            showToast(
                    context,
                    String.format(
                            context.getString(R.string.isForegroundApplication),
                            label
                    )
            );
    }

    public static void processUnfreezeAction(Context context, String pkgName, String target, String tasks, boolean askRun, boolean runImmediately, Activity activity, boolean finish) {
        startService(context, new Intent(context, FUFService.class)
                .putExtra("askRun", askRun)
                .putExtra("pkgName", pkgName)
                .putExtra("freeze", false)
                .putExtra("target", target)//目标 Activity
                .putExtra("tasks", tasks)//追加任务
                .putExtra("single", true)
                .putExtra("runImmediately", runImmediately));
        checkAndDoActivityFinish(activity, finish);
    }

    public static void processFreezeAction(Context context, String pkgName, String target, String tasks, boolean askRun, Activity activity, boolean finish) {
        startService(context, new Intent(context, FUFService.class)
                .putExtra("askRun", askRun)
                .putExtra("pkgName", pkgName)
                .putExtra("target", target)//目标 Activity
                .putExtra("tasks", tasks)//追加任务
                .putExtra("freeze", true)
                .putExtra("single", true));
        checkAndDoActivityFinish(activity, finish);
    }

    public static boolean checkMRootFrozen(Context context, String pkgName) {
        try {
            return Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && DevicePolicyManagerUtils.isDeviceOwner(context) && getDevicePolicyManager(context).isApplicationHidden(DeviceAdminReceiver.getComponentName(context), pkgName);
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean checkRootFrozen(Context context, String pkgName, PackageManager packageManager) {
        int tmp;
        try {
            tmp = packageManager == null ? context.getPackageManager().getApplicationEnabledSetting(pkgName) : packageManager.getApplicationEnabledSetting(pkgName);
        } catch (Exception e) {
            tmp = -1;
        }
        return ((tmp == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER) || (tmp == PackageManager.COMPONENT_ENABLED_STATE_DISABLED));
    }

    /**
     * @param packageName 应用包名
     * @return true 则已冻结
     */
    public static boolean realGetFrozenStatus(Context context, String packageName, PackageManager pm) {
        return (checkRootFrozen(context, packageName, pm) || checkMRootFrozen(context, packageName));
    }

    public static void checkFrozenStatusAndStartApp(Context context, String pkgName, String target, String tasks) {
        if (realGetFrozenStatus(context, pkgName, null)) {
            processUnfreezeAction(context, pkgName, target, tasks, true, true, null, false);
        } else {
            checkAndStartApp(context, pkgName, target, tasks, null, false);
        }
    }

    public static boolean checkRootPermission() {
        boolean hasPermission = true;
        int value = -1;
        try {
            Process process = Runtime.getRuntime().exec("su");
            DataOutputStream outputStream = new DataOutputStream(process.getOutputStream());
            outputStream.writeBytes("exit\n");
            outputStream.flush();
            value = process.waitFor();
            try {
                outputStream.close();
                process.destroy();
            } catch (Exception e) {
                e.printStackTrace();
            }
        } catch (Exception e) {
            if (e.getMessage().toLowerCase().contains("permission denied") || e.getMessage().toLowerCase().contains("not found")) {
                hasPermission = false;
            }
        }
        return hasPermission && value == 0;
    }

    public static void checkAndCreateFUFQuickNotification(Context context, String pkgName) {
        if (new AppPreferences(context)
                .getBoolean("createQuickFUFNotiAfterUnfrozen", true)) {
            createFUFQuickNotification(
                    context, pkgName, R.drawable.ic_notification,
                    getBitmapFromDrawable(
                            getApplicationIcon(
                                    context,
                                    pkgName,
                                    getApplicationInfoFromPkgName(pkgName, context),
                                    false
                            )
                    )
            );
        }
    }

}
