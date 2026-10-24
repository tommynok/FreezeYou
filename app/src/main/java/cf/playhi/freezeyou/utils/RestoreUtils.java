package cf.playhi.freezeyou.utils;

import android.app.Activity;
import androidx.appcompat.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
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

/**
 * Utility class for detecting and restoring uninstalled system applications.
 *
 * <p><b>Android System App Lifecycle Rationale:</b><br>
 * When a system app residing in read-only partitions ({@code /system}, {@code /product},
 * {@code /vendor}, {@code /system_ext}) is uninstalled for a specific Android user (e.g. via
 * {@code pm uninstall -k --user 0} or {@link android.content.pm.PackageManager#DELETE_SYSTEM_APP}),
 * its APK binaries remain intact in the system image. The framework merely removes the user's
 * private data directory from {@code /data/user/<id>/} and unsets the {@code FLAG_INSTALLED}
 * bit in {@link ApplicationInfo#flags}.
 *
 * <p>Unlike third-party user apps (whose APKs in {@code /data/app} are permanently wiped upon
 * uninstallation), system apps can be restored instantly without APK re-downloading or root remounting
 * using Android's built-in {@code cmd package install-existing} (or {@code pm install-existing}) command.
 *
 * <p><b>Privilege Model:</b><br>
 * Restoration requires privileged execution permissions ({@code android.permission.INSTALL_PACKAGES}),
 * granted via either Shizuku (UID 2000 shell) or Root (UID 0). Shizuku is always prioritized.
 */
public final class RestoreUtils {

    /**
     * Hidden framework flag indicating that the package is currently installed for the targeted user.
     * Value corresponds to {@code ApplicationInfo.FLAG_INSTALLED} (1 &lt;&lt; 23, 0x00800000).
     * Defined locally to ensure safe compilation and runtime checks without reflection or hidden API bypass.
     */
    private static final int FLAG_INSTALLED = 0x00800000;

    private static boolean isActivityAlive(Activity activity) {
        return activity != null && !activity.isFinishing() && (Build.VERSION.SDK_INT < 17 || !activity.isDestroyed());
    }

    private RestoreUtils() {}

    /**
     * Checks if the filter string corresponds to the "Only Uninstalled System" (OUS) category.
     */
    public static boolean isRestoreFilter(String filter) {
        return "OUS".equals(filter);
    }

    /**
     * Checks if the package represented by {@code appInfo} is marked as uninstalled for the current user.
     */
    public static boolean isAppUninstalled(ApplicationInfo appInfo) {
        return appInfo != null && isAppUninstalledFlags(appInfo.flags);
    }

    /**
     * Evaluates package flags to determine if {@link #FLAG_INSTALLED} is absent.
     */
    public static boolean isAppUninstalledFlags(int flags) {
        return (flags & FLAG_INSTALLED) == 0;
    }

    /**
     * Checks if the application is a restorable system app: it must have
     * {@link ApplicationInfo#FLAG_SYSTEM} set and {@link #FLAG_INSTALLED} unset.
     * User applications cannot be restored via {@code install-existing} because their APKs
     * in {@code /data/app} are wiped upon uninstallation.
     */
    public static boolean isRestorableSystemApp(ApplicationInfo appInfo) {
        return appInfo != null && isRestorableSystemAppFlags(appInfo.flags);
    }

    /**
     * Evaluates package flags to verify whether the package is a system app and currently uninstalled.
     */
    public static boolean isRestorableSystemAppFlags(int flags) {
        return (flags & ApplicationInfo.FLAG_SYSTEM) != 0 && isAppUninstalledFlags(flags);
    }

    /**
     * Queries {@link PackageManager} with {@code GET_UNINSTALLED_PACKAGES} to determine
     * whether the specified package exists in the system partition and is currently uninstalled for this user.
     */
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

    /**
     * Validates that every package in the provided list is currently an uninstalled system package
     * eligible for restoration. Returns false if any package is invalid, already installed, or a user app.
     */
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

    /**
     * Shows a confirmation dialog to the user before restoring uninstalled system apps.
     *
     * @param activity active activity hosting the dialog
     * @param packageNames list of package names to restore
     * @param appName user-visible label of the app (or null for batch operations)
     * @param onRestoredCallback callback executed on the main looper after restoration completes
     */
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

    /**
     * Convenience overload for restoring multiple packages without a specific app name label.
     */
    public static void restorePackages(final Context context,
                                       final List<String> packageNames,
                                       final Runnable onFinishedCallback) {
        restorePackages(context, packageNames, null, onFinishedCallback);
    }

    /**
     * Dispatches restoration of the specified system packages on a background thread.
     *
     * <p><b>Execution flow:</b>
     * <ol>
     *   <li>Deduplicates package names using {@link LinkedHashSet} to avoid redundant operations.</li>
     *   <li>Verifies eligibility with {@link #canRestorePackages(Context, List)}.</li>
     *   <li>Displays an indeterminate {@link ProgressDialog} if batch restoring (&gt; 1 package) in an Activity.</li>
     *   <li>Iterates packages and executes {@code cmd package install-existing --user <userId> <package>}.
     *       If that fails, falls back to legacy {@code pm install-existing --user <userId> <package>}.</li>
     *   <li>Prioritizes Shizuku execution; if unavailable, uses Root.</li>
     *   <li>Dismisses progress dialog and posts results to the main thread via {@link #postRestoreResult}.</li>
     * </ol>
     *
     * @param context execution context
     * @param packageNames list of package names to restore
     * @param appName user-facing label (for single-app restoration dialog) or null
     * @param onFinishedCallback callback executed on the main thread when operation concludes
     */
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
        final Activity activity = context instanceof Activity ? (Activity) context : null;
        if (packages.size() > 1 && isActivityAlive(activity)) {
            try {
                progressDialog = new ProgressDialog(activity);
                progressDialog.setMessage(activity.getString(R.string.batch_restoring));
                progressDialog.setCancelable(false);
                progressDialog.setCanceledOnTouchOutside(false);
                progressDialog.show();
            } catch (Exception ignored) {}
        }
        final ProgressDialog finalProgressDialog = progressDialog;
        final Context appContext = context.getApplicationContext();

        new Thread(() -> {
            final boolean shizukuAvailable = PrivilegedShellUtils.isShizukuAvailable();
            final boolean rootAvailable = !shizukuAvailable && FUFUtils.checkRootPermission();
            int restoredCount = 0;

            for (String packageName : packages) {
                // Recheck just before running the command; the list/menu can be stale by now.
                if (!isRestorableSystemPackage(appContext, packageName)) continue;
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

            final int finalRestoredCount = restoredCount;
            new Handler(Looper.getMainLooper()).post(() -> {
                if (finalProgressDialog != null) {
                    try {
                        if (isActivityAlive(activity) && finalProgressDialog.isShowing()) {
                            finalProgressDialog.dismiss();
                        }
                    } catch (Exception ignored) {}
                }

                postRestoreResult(context, finalRestoredCount, packages.size(), appName, onFinishedCallback);
            });
        }).start();
    }

    /**
     * Executes the shell command via Shizuku (preferred) or Root (fallback).
     */
    private static PrivilegedShellUtils.CommandResult runInstallExisting(
            boolean shizukuAvailable,
            String command) throws Exception {
        return shizukuAvailable
                ? PrivilegedShellUtils.runShizukuCommand(command)
                : PrivilegedShellUtils.runRootCommand(command);
    }

    /**
     * Dispatches restoration feedback to the user on the main looper.
     *
     * <p>Respects the "Stealth" preference ({@link #hideUninstallSuccessDialog}):
     * when stealth mode is active, success dialogs and toasts are silenced, notifying
     * the user only if the operation failed completely.
     */
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
                } else if (context instanceof Activity && isActivityAlive((Activity) context)) {
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

    /**
     * Displays a Material completion dialog summarizing restored package counts.
     * Applies accent color tinting to positive dialog buttons when running under light themes.
     */
    private static void showRestoreCompletedDialog(
            final Activity activity,
            final int restoredCount,
            final int totalCount,
            final String appName) {
        if (!isActivityAlive(activity)) {
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
