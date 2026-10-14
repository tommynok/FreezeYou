package cf.playhi.freezeyou.utils;

import android.app.Activity;
import androidx.appcompat.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;

import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import cf.playhi.freezeyou.R;

import static cf.playhi.freezeyou.app.FreezeYouAlertDialogBuilderKt.FreezeYouAlertDialogBuilder;
import static cf.playhi.freezeyou.storage.key.DefaultMultiProcessMMKVStorageBooleanKeys.hideUninstallSuccessDialog;
import static cf.playhi.freezeyou.utils.ThemeUtils.isLightUiTheme;
import static cf.playhi.freezeyou.utils.ToastUtils.showShortToast;

public final class RestoreUtils {

    // FLAG_INSTALLED is 1 << 23. Keep the value local so it can be checked on older Android APIs.
    private static final int FLAG_INSTALLED = 0x00800000;

    private RestoreUtils() {}

    public static boolean isRestoreFilter(String filter) {
        return "OUS".equals(filter);
    }

    public static boolean isAppUninstalled(ApplicationInfo appInfo) {
        return appInfo != null && isAppUninstalledFlags(appInfo.flags);
    }

    public static boolean isAppUninstalledFlags(int flags) {
        return (flags & FLAG_INSTALLED) == 0;
    }

    public static boolean isRestorableSystemApp(ApplicationInfo appInfo) {
        return appInfo != null && isRestorableSystemAppFlags(appInfo.flags);
    }

    public static boolean isRestorableSystemAppFlags(int flags) {
        return (flags & ApplicationInfo.FLAG_SYSTEM) != 0 && isAppUninstalledFlags(flags);
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
            showShortToast(activity, R.string.restore_failed);
            return;
        }

        FreezeYouAlertDialogBuilder(activity)
                .setTitle(appName)
                .setMessage(R.string.app_is_uninstalled_restore_prompt)
                .setPositiveButton(R.string.restore, (dialog, which) -> {
                    restorePackages(activity, packageNames, appName, onRestoredCallback);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    public static void restorePackages(final Context context,
                                       final List<String> packageNames,
                                       final Runnable onFinishedCallback) {
        restorePackages(context, packageNames, null, onFinishedCallback);
    }

    public static void restorePackages(final Context context,
                                       final List<String> packageNames,
                                       final String appName,
                                       final Runnable onFinishedCallback) {
        if (context == null || packageNames == null || packageNames.isEmpty()) return;

        Set<String> uniqueNames = new LinkedHashSet<>(packageNames);
        final List<String> packages = new ArrayList<>(uniqueNames);
        if (!canRestorePackages(context, packages)) {
            postRestoreResult(context, 0, packages.size(), appName, onFinishedCallback);
            return;
        }

        ProgressDialog progressDialog = null;
        if (packages.size() > 1 && context instanceof Activity && !((Activity) context).isFinishing()) {
            try {
                progressDialog = new ProgressDialog(context);
                progressDialog.setMessage(context.getString(R.string.batch_restoring));
                progressDialog.setCancelable(false);
                progressDialog.setCanceledOnTouchOutside(false);
                progressDialog.show();
            } catch (Exception ignored) {}
        }
        final ProgressDialog finalProgressDialog = progressDialog;

        new Thread(() -> {
            final boolean shizukuAvailable = PrivilegedShellUtils.isShizukuAvailable();
            final boolean rootAvailable = !shizukuAvailable && FUFUtils.checkRootPermission();
            int restoredCount = 0;

            for (String packageName : packages) {
                // Recheck just before running the command; the list/menu can be stale by now.
                if (!isRestorableSystemPackage(context, packageName)) continue;
                if (!shizukuAvailable && !rootAvailable) continue;

                String quotedPackage = PrivilegedShellUtils.shellQuote(packageName);
                String userOption = "--user " + AndroidUserUtils.currentUserId() + " ";
                try {
                    PrivilegedShellUtils.CommandResult result = runInstallExisting(
                            shizukuAvailable,
                            "cmd package install-existing " + userOption + quotedPackage);
                    if (!result.isSuccessful()) {
                        result = runInstallExisting(
                                shizukuAvailable,
                                "pm install-existing " + userOption + quotedPackage);
                    }
                    if (result.isSuccessful()) restoredCount++;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            if (finalProgressDialog != null) {
                try {
                    if (finalProgressDialog.isShowing()) {
                        finalProgressDialog.dismiss();
                    }
                } catch (Exception ignored) {}
            }

            postRestoreResult(context, restoredCount, packages.size(), appName, onFinishedCallback);
        }).start();
    }

    private static PrivilegedShellUtils.CommandResult runInstallExisting(
            boolean shizukuAvailable,
            String command) throws Exception {
        return shizukuAvailable
                ? PrivilegedShellUtils.runShizukuCommand(command)
                : PrivilegedShellUtils.runRootCommand(command);
    }

    private static void postRestoreResult(final Context context,
                                          final int restoredCount,
                                          final int totalCount,
                                          final String appName,
                                          final Runnable onFinishedCallback) {
        new Handler(Looper.getMainLooper()).post(() -> {
            boolean stealth = hideUninstallSuccessDialog.getValue(context);
            if (!stealth) {
                if (restoredCount == 0) {
                    showShortToast(context, R.string.restore_failed);
                } else if (context instanceof Activity && !((Activity) context).isFinishing()) {
                    showRestoreCompletedDialog((Activity) context, restoredCount, totalCount, appName);
                } else {
                    if (restoredCount == totalCount) {
                        showShortToast(context, R.string.restore_completed);
                    } else {
                        showShortToast(context, context.getString(
                                R.string.restore_partial, restoredCount, totalCount));
                    }
                }
            } else if (restoredCount == 0) {
                showShortToast(context, R.string.restore_failed);
            }
            if (onFinishedCallback != null) onFinishedCallback.run();
        });
    }

    private static void showRestoreCompletedDialog(
            final Activity activity,
            final int restoredCount,
            final int totalCount,
            final String appName) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            return;
        }

        String message;
        if (totalCount > 1) {
            message = activity.getString(R.string.restore_partial, restoredCount, totalCount);
        } else if (appName != null && !appName.isEmpty()) {
            message = activity.getString(R.string.restore_completed_single_app, appName);
        } else {
            message = activity.getString(R.string.restore_completed);
        }

        AlertDialog dialog = FreezeYouAlertDialogBuilder(activity)
                .setIcon(R.mipmap.ic_launcher_new_round)
                .setTitle(R.string.restore_completed_title)
                .setMessage(message)
                .setPositiveButton(R.string.okay, null)
                .create();
        dialog.setCanceledOnTouchOutside(true);
        if (isLightUiTheme(activity)) {
            dialog.setOnShowListener(d -> {
                Button positiveButton = dialog.getButton(DialogInterface.BUTTON_POSITIVE);
                if (positiveButton != null) {
                    positiveButton.setTextColor(ContextCompat.getColor(activity, R.color.appAccent));
                }
            });
        }
        dialog.show();
    }
}
