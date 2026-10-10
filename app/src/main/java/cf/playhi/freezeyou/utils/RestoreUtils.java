package cf.playhi.freezeyou.utils;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import cf.playhi.freezeyou.R;

import static cf.playhi.freezeyou.app.FreezeYouAlertDialogBuilderKt.FreezeYouAlertDialogBuilder;
import static cf.playhi.freezeyou.utils.ToastUtils.showToast;

public final class RestoreUtils {

    // FLAG_INSTALLED is 1 << 23. Keep the value local so it can be checked on older Android APIs.
    private static final int FLAG_INSTALLED = 0x00800000;

    private RestoreUtils() {}

    public static boolean isAppUninstalled(ApplicationInfo appInfo) {
        return appInfo != null && (appInfo.flags & FLAG_INSTALLED) == 0;
    }

    public static boolean isRestorableSystemApp(ApplicationInfo appInfo) {
        return appInfo != null
                && (appInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0
                && isAppUninstalled(appInfo);
    }

    public static boolean areAllRestorableSystemApps(List<ApplicationInfo> appInfos) {
        if (appInfos == null || appInfos.isEmpty()) return false;
        for (ApplicationInfo appInfo : appInfos) {
            if (!isRestorableSystemApp(appInfo)) return false;
        }
        return true;
    }

    public static boolean isRestorableSystemPackage(Context context, String packageName) {
        if (context == null || packageName == null || packageName.isEmpty()) return false;
        try {
            ApplicationInfo appInfo = context.getPackageManager().getApplicationInfo(
                    packageName, PackageManager.GET_UNINSTALLED_PACKAGES);
            return isRestorableSystemApp(appInfo);
        } catch (PackageManager.NameNotFoundException | SecurityException ignored) {
            return false;
        }
    }

    /** True only when every requested package is currently an uninstalled system package. */
    public static boolean canRestorePackages(Context context, List<String> packageNames) {
        if (context == null || packageNames == null || packageNames.isEmpty()) return false;
        PackageManager packageManager = context.getPackageManager();
        for (String packageName : packageNames) {
            if (packageName == null || packageName.isEmpty()) return false;
            try {
                ApplicationInfo appInfo = packageManager.getApplicationInfo(
                        packageName, PackageManager.GET_UNINSTALLED_PACKAGES);
                if (!isRestorableSystemApp(appInfo)) return false;
            } catch (PackageManager.NameNotFoundException | SecurityException ignored) {
                return false;
            }
        }
        return true;
    }

    public static void showRestoreConfirmDialog(final Activity activity,
                                                final List<String> packageNames,
                                                final String appName,
                                                final Runnable onRestoredCallback) {
        if (activity == null || activity.isFinishing()) return;
        if (!canRestorePackages(activity, packageNames)) {
            showToast(activity, R.string.restore_failed);
            return;
        }

        FreezeYouAlertDialogBuilder(activity)
                .setTitle(appName)
                .setMessage(R.string.app_is_uninstalled_restore_prompt)
                .setPositiveButton(R.string.restore, (dialog, which) -> {
                    restorePackages(activity, packageNames, onRestoredCallback);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    public static void restorePackages(final Context context,
                                       final List<String> packageNames,
                                       final Runnable onFinishedCallback) {
        if (context == null || packageNames == null || packageNames.isEmpty()) return;

        Set<String> uniqueNames = new LinkedHashSet<>(packageNames);
        final List<String> packages = new ArrayList<>(uniqueNames);
        if (!canRestorePackages(context, packages)) {
            postRestoreResult(context, 0, packages.size(), onFinishedCallback);
            return;
        }

        showToast(context, String.format(
                context.getString(R.string.restoring_app),
                packages.size() == 1 ? packages.get(0) : ""));

        new Thread(() -> {
            final boolean shizukuAvailable = PrivilegedShellUtils.isShizukuAvailable();
            final boolean rootAvailable = !shizukuAvailable && FUFUtils.checkRootPermission();
            int restoredCount = 0;

            for (String packageName : packages) {
                // Recheck just before running the command; the list/menu can be stale by now.
                if (!isRestorableSystemPackage(context, packageName)) continue;
                if (!shizukuAvailable && !rootAvailable) continue;

                String quotedPackage = PrivilegedShellUtils.shellQuote(packageName);
                try {
                    PrivilegedShellUtils.CommandResult result = runInstallExisting(
                            shizukuAvailable,
                            "cmd package install-existing " + quotedPackage);
                    if (!result.isSuccessful()) {
                        result = runInstallExisting(
                                shizukuAvailable,
                                "pm install-existing " + quotedPackage);
                    }
                    if (result.isSuccessful()) restoredCount++;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            postRestoreResult(context, restoredCount, packages.size(), onFinishedCallback);
        }).start();
    }

    private static PrivilegedShellUtils.CommandResult runInstallExisting(
            boolean shizukuAvailable,
            String command) throws Exception {
        return shizukuAvailable
                ? PrivilegedShellUtils.runShizukuCommand(command)
                : PrivilegedShellUtils.runRootCommand(command);
    }

    private static void postRestoreResult(Context context,
                                          int restoredCount,
                                          int totalCount,
                                          Runnable onFinishedCallback) {
        new Handler(Looper.getMainLooper()).post(() -> {
            if (restoredCount == totalCount) {
                showToast(context, R.string.restore_completed);
            } else if (restoredCount == 0) {
                showToast(context, R.string.restore_failed);
            } else {
                showToast(context, context.getString(
                        R.string.restore_partial, restoredCount, totalCount));
            }
            if (onFinishedCallback != null) onFinishedCallback.run();
        });
    }
}
