package cf.playhi.freezeyou.utils;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;

import java.io.DataOutputStream;
import java.util.Collections;
import java.util.List;

import cf.playhi.freezeyou.R;
import static cf.playhi.freezeyou.app.FreezeYouAlertDialogBuilderKt.FreezeYouAlertDialogBuilder;
import static cf.playhi.freezeyou.utils.ToastUtils.showToast;

public final class RestoreUtils {

    private RestoreUtils() {}

    public static boolean isAppUninstalled(ApplicationInfo appInfo) {
        if (appInfo == null) return false;
        // In Android, FLAG_INSTALLED is 1 << 23 (0x800000).
        // If this bit is 0, the app is not installed for the current user.
        return (appInfo.flags & 0x00800000) == 0;
    }

    public static boolean isPackageUninstalled(Context context, String packageName) {
        if (packageName == null || context == null) return false;
        try {
            ApplicationInfo appInfo = context.getPackageManager().getApplicationInfo(
                    packageName, PackageManager.GET_UNINSTALLED_PACKAGES
            );
            return isAppUninstalled(appInfo);
        } catch (Throwable e) {
            return false;
        }
    }

    public static void showRestoreConfirmDialog(final android.app.Activity activity, final java.util.List<String> packageNames, final String appName, final Runnable onRestoredCallback) {
        if (activity == null || activity.isFinishing()) return;

        cf.playhi.freezeyou.utils.AlertDialogUtils.FreezeYouAlertDialogBuilder(activity)
                .setTitle(appName)
                .setMessage(R.string.app_is_uninstalled_restore_prompt)
                .setPositiveButton(R.string.restore, (dialog, which) -> {
                    restorePackages(activity, packageNames, onRestoredCallback);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    public static void restorePackages(final Context context, final List<String> packageNames, final Runnable onFinishedCallback) {
        if (packageNames == null || packageNames.isEmpty()) return;

        showToast(context, String.format(context.getString(R.string.restoring_app), packageNames.size() == 1 ? packageNames.get(0) : ""));

        new Thread(() -> {
            boolean overallSuccess = false;
            boolean shizukuAvailable = false;
            try {
                shizukuAvailable = rikka.shizuku.Shizuku.pingBinder()
                        && rikka.shizuku.Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
            } catch (Throwable ignored) {}

            for (String pkg : packageNames) {
                String cmd1 = "cmd package install-existing \"" + pkg + "\"";
                String cmd2 = "pm install-existing \"" + pkg + "\"";

                if (shizukuAvailable) {
                    try {
                        Process p = rikka.shizuku.Shizuku.newProcess(new String[]{"sh", "-c", cmd1 + " || " + cmd2}, null, null);
                        p.waitFor();
                        overallSuccess = true;
                    } catch (Throwable e) {
                        e.printStackTrace();
                    }
                } else if (FUFUtils.checkRootPermission()) {
                    try {
                        Process process = Runtime.getRuntime().exec("su");
                        DataOutputStream outputStream = new DataOutputStream(process.getOutputStream());
                        outputStream.writeBytes(cmd1 + " || " + cmd2 + "\n");
                        outputStream.writeBytes("exit\n");
                        outputStream.flush();
                        process.waitFor();
                        ProcessUtils.destroyProcess(outputStream, process);
                        overallSuccess = true;
                    } catch (Throwable e) {
                        e.printStackTrace();
                    }
                }
            }

            final boolean success = overallSuccess;
            new Handler(Looper.getMainLooper()).post(() -> {
                showToast(context, success ? R.string.restore_completed : R.string.restore_failed);
                if (onFinishedCallback != null) {
                    onFinishedCallback.run();
                }
            });
        }).start();
    }
}
