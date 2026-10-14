package cf.playhi.freezeyou.utils;

import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.IBinder;
import android.os.IInterface;
import android.util.Log;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import rikka.shizuku.ShizukuBinderWrapper;
import rikka.shizuku.SystemServiceHelper;

/**
 * High-performance package uninstallation using direct Binder IPC with Shizuku credentials,
 * reproducing Canta's architecture 1-to-1.
 *
 * Connects directly to the framework's IPackageInstaller via ShizukuBinderWrapper,
 * instantiates client PackageInstaller, and captures uninstallation completion in-process
 * via LocalIntentSender without slow shell processes (pm uninstall) or delayed broadcasts.
 */
public final class ShizukuPackageInstaller {

    // Flags from android.content.pm.PackageManager
    public static final int DELETE_ALL_USERS = 0x00000002;
    public static final int DELETE_SYSTEM_APP = 0x00000004;

    private static final String INSTALLER_PACKAGE = "com.android.shell";
    private static final long RESULT_TIMEOUT_MS = 15000L;

    private ShizukuPackageInstaller() {}

    /**
     * Uninstalls a package via direct Shizuku Binder IPC.
     *
     * @param context application or activity context
     * @param packageName package to uninstall
     * @param isSystemApp whether package is a system app
     * @param hasSystemUpdate whether system app has updates
     * @param uninstallUpdatesOnly if true, only revert update; if false, uninstall completely
     * @return true if uninstallation succeeded via Binder IPC
     */
    public static boolean uninstall(
            Context context,
            String packageName,
            boolean isSystemApp,
            boolean hasSystemUpdate,
            boolean uninstallUpdatesOnly) {

        if (!PrivilegedShellUtils.isShizukuAvailable()) {
            return false;
        }

        try {
            int userId = AndroidUserUtils.currentUserId();
            boolean debug = DebugModeUtils.isDebugModeEnabled();
            if (debug) {
                Log.d("FreezeYouDebug", "ShizukuPackageInstaller.uninstall started for " + packageName
                        + " (isSystem=" + isSystemApp + ", hasUpdate=" + hasSystemUpdate
                        + ", updatesOnly=" + uninstallUpdatesOnly + ", userId=" + userId + ")");
            }

            PackageInstaller installer = getPackageInstaller(userId);

            if (isSystemApp && hasSystemUpdate) {
                if (uninstallUpdatesOnly) {
                    // Revert update only: flags = DELETE_SYSTEM_APP
                    return performSingleUninstall(context, installer, packageName, DELETE_SYSTEM_APP, true);
                } else {
                    // Complete uninstall:
                    // Step 1: Revert system update (DELETE_SYSTEM_APP)
                    boolean step1 = performSingleUninstall(context, installer, packageName, DELETE_SYSTEM_APP, true);
                    if (!step1) {
                        if (debug) {
                            Log.w("FreezeYouDebug", "Step 1 (revert update) failed for " + packageName);
                        }
                        return false;
                    }
                    // Step 2: Disable / uninstall factory package for user (DELETE_SYSTEM_APP)
                    return performSingleUninstall(context, installer, packageName, DELETE_SYSTEM_APP, false);
                }
            } else if (isSystemApp) {
                // Non-updated system app: flags = DELETE_SYSTEM_APP
                return performSingleUninstall(context, installer, packageName, DELETE_SYSTEM_APP, false);
            } else {
                // User app: flags = DELETE_ALL_USERS
                return performSingleUninstall(context, installer, packageName, DELETE_ALL_USERS, false);
            }
        } catch (Throwable t) {
            t.printStackTrace();
            if (DebugModeUtils.isDebugModeEnabled()) {
                Log.e("FreezeYouDebug", "ShizukuPackageInstaller.uninstall error: " + t.getMessage(), t);
            }
            return false;
        }
    }

    private static boolean performSingleUninstall(
            Context context,
            PackageInstaller installer,
            String packageName,
            int flags,
            boolean isRemovingUpdateOnly) throws Exception {

        long startTime = System.currentTimeMillis();
        boolean debug = DebugModeUtils.isDebugModeEnabled();

        LocalIntentSender receiver = new LocalIntentSender();
        IntentSender sender = receiver.getIntentSender();

        if (debug) {
            Log.d("FreezeYouDebug", "performSingleUninstall: calling PackageInstaller.uninstall for "
                    + packageName + " with flags=" + flags);
        }

        invokeUninstall(installer, packageName, flags, sender);

        Intent resultIntent = receiver.await(RESULT_TIMEOUT_MS);
        if (resultIntent != null) {
            int status = resultIntent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
            String message = resultIntent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
            if (debug) {
                Log.d("FreezeYouDebug", "performSingleUninstall received status=" + status
                        + " (" + message + ") in " + (System.currentTimeMillis() - startTime) + " ms");
            }
            if (status == PackageInstaller.STATUS_SUCCESS) {
                return true;
            }
        } else {
            if (debug) {
                Log.w("FreezeYouDebug", "performSingleUninstall: timed out awaiting result for " + packageName);
            }
        }

        // Fallback verification: check if package or update was actually removed
        boolean verified;
        if (isRemovingUpdateOnly) {
            verified = !hasUpdatedSystemAppFlag(context, packageName);
        } else {
            verified = !isPackageInstalled(context, packageName);
        }

        if (debug) {
            Log.d("FreezeYouDebug", "performSingleUninstall fallback verification result: "
                    + verified + " in " + (System.currentTimeMillis() - startTime) + " ms for " + packageName);
        }
        return verified;
    }

    private static PackageInstaller getPackageInstaller(int userId) throws Exception {
        LocalIntentSender.allowHiddenApis();

        IBinder packageBinder = SystemServiceHelper.getSystemService("package");
        if (packageBinder == null) {
            throw new IllegalStateException("Failed to obtain system service: package");
        }

        Class<?> pmStubClass = Class.forName("android.content.pm.IPackageManager$Stub");
        Method pmAsInterface = pmStubClass.getMethod("asInterface", IBinder.class);
        Object pmProxy = pmAsInterface.invoke(null, new ShizukuBinderWrapper(packageBinder));
        if (pmProxy == null) {
            throw new IllegalStateException("Failed to get IPackageManager proxy");
        }

        Method getPackageInstallerMethod = pmProxy.getClass().getMethod("getPackageInstaller");
        Object rawInstaller = getPackageInstallerMethod.invoke(pmProxy);
        if (rawInstaller == null) {
            throw new IllegalStateException("Failed to get raw IPackageInstaller");
        }

        IBinder installerRawBinder = ((IInterface) rawInstaller).asBinder();
        Class<?> installerStubClass = Class.forName("android.content.pm.IPackageInstaller$Stub");
        Method installerAsInterface = installerStubClass.getMethod("asInterface", IBinder.class);
        Object privilegedInstaller = installerAsInterface.invoke(null, new ShizukuBinderWrapper(installerRawBinder));

        Class<?> iPackageInstallerClass = Class.forName("android.content.pm.IPackageInstaller");

        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.R) { // SDK > 30 (Android 12+)
            try {
                Constructor<PackageInstaller> ctor = PackageInstaller.class.getConstructor(
                        iPackageInstallerClass, String.class, String.class, int.class);
                return ctor.newInstance(privilegedInstaller, INSTALLER_PACKAGE, null, userId);
            } catch (NoSuchMethodException ignored) {
            }
        }

        Constructor<PackageInstaller> ctor = PackageInstaller.class.getConstructor(
                iPackageInstallerClass, String.class, int.class);
        return ctor.newInstance(privilegedInstaller, INSTALLER_PACKAGE, userId);
    }

    private static void invokeUninstall(
            PackageInstaller installer,
            String packageName,
            int flags,
            IntentSender statusSender) throws Exception {

        Method uninstallMethod = null;
        try {
            uninstallMethod = PackageInstaller.class.getMethod(
                    "uninstall", String.class, int.class, IntentSender.class);
            uninstallMethod.invoke(installer, packageName, flags, statusSender);
            return;
        } catch (NoSuchMethodException ignored) {
        }

        try {
            Class<?> vpClass = Class.forName("android.content.pm.VersionedPackage");
            Constructor<?> vpCtor = vpClass.getConstructor(String.class, int.class);
            Object vp = vpCtor.newInstance(packageName, -1);
            uninstallMethod = PackageInstaller.class.getMethod(
                    "uninstall", vpClass, int.class, IntentSender.class);
            uninstallMethod.invoke(installer, vp, flags, statusSender);
            return;
        } catch (NoSuchMethodException ignored) {
        }

        uninstallMethod = PackageInstaller.class.getMethod(
                "uninstall", String.class, IntentSender.class);
        uninstallMethod.invoke(installer, packageName, statusSender);
    }

    public static void invalidateCache() {
        // Retained for backward compatibility
    }

    private static boolean isPackageInstalled(Context context, String packageName) {
        try {
            context.getPackageManager().getPackageInfo(packageName, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private static boolean hasUpdatedSystemAppFlag(Context context, String packageName) {
        try {
            android.content.pm.PackageInfo pi = context.getPackageManager().getPackageInfo(packageName, 0);
            return pi != null && pi.applicationInfo != null
                    && (pi.applicationInfo.flags & android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }
}
