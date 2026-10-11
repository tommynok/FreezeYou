package cf.playhi.freezeyou.utils;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.IBinder;
import android.os.IInterface;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import rikka.shizuku.ShizukuBinderWrapper;
import rikka.shizuku.SystemServiceHelper;

/**
 * High-performance package uninstallation using direct Binder IPC with Shizuku credentials,
 * inspired by Canta's architecture.
 *
 * Calls IPackageInstaller directly through ShizukuBinderWrapper without spawning slow
 * shell/CLI processes (pm uninstall). Automatically adapts to Android version differences
 * and falls back to shell CLI if Binder calls fail.
 */
public final class ShizukuPackageInstaller {

    private static final String ACTION_UNINSTALL_RESULT =
            "cf.playhi.freezeyou.action.SHIZUKU_UNINSTALL_RESULT";

    // Flags from android.content.pm.PackageManager
    public static final int DELETE_ALL_USERS = 0x00000002;
    public static final int DELETE_SYSTEM_APP = 0x00000004;

    private static volatile Object sCachedPackageInstallerProxy = null;
    private static volatile Method sCachedUninstallMethod = null;

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

            if (isSystemApp && hasSystemUpdate) {
                if (uninstallUpdatesOnly) {
                    // Revert update only: flags = 0
                    return performBinderUninstall(context, packageName, 0, userId);
                } else {
                    // Complete uninstall: 2 steps
                    // Step 1: Revert system update (flags = 0)
                    boolean step1 = performBinderUninstall(context, packageName, 0, userId);
                    // Step 2: Disable/uninstall factory package for user (flags = DELETE_SYSTEM_APP)
                    boolean step2 = performBinderUninstall(context, packageName, DELETE_SYSTEM_APP, userId);
                    return step1 && step2;
                }
            } else if (isSystemApp) {
                // Non-updated system app: flags = DELETE_SYSTEM_APP
                return performBinderUninstall(context, packageName, DELETE_SYSTEM_APP, userId);
            } else {
                // User app: flags = DELETE_ALL_USERS
                return performBinderUninstall(context, packageName, DELETE_ALL_USERS, userId);
            }
        } catch (Throwable t) {
            t.printStackTrace();
            invalidateCache();
            return false;
        }
    }

    private static boolean performBinderUninstall(
            Context context,
            String packageName,
            int flags,
            int userId) throws Exception {

        Object installerProxy = getOrCreatePackageInstallerProxy();
        Method uninstallMethod = getOrCreateUninstallMethod(installerProxy);

        final String uniqueAction = ACTION_UNINSTALL_RESULT + "." + UUID.randomUUID().toString();
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicInteger resultStatus = new AtomicInteger(-1);

        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                if (intent != null) {
                    int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1);
                    resultStatus.set(status);
                    latch.countDown();
                }
            }
        };

        IntentFilter filter = new IntentFilter(uniqueAction);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            context.registerReceiver(receiver, filter);
        }

        try {
            Intent statusIntent = new Intent(uniqueAction).setPackage(context.getPackageName());
            int pendingFlags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                    ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE
                    : PendingIntent.FLAG_UPDATE_CURRENT;

            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                    context,
                    packageName.hashCode(),
                    statusIntent,
                    pendingFlags
            );
            IntentSender statusReceiver = pendingIntent.getIntentSender();

            Object[] args = buildUninstallArgs(uninstallMethod, packageName, "com.android.shell", flags, statusReceiver, userId);
            uninstallMethod.invoke(installerProxy, args);

            // Await async broadcast from system_server (typically 20-200ms)
            boolean completed = latch.await(4, TimeUnit.SECONDS);
            if (completed && resultStatus.get() == PackageInstaller.STATUS_SUCCESS) {
                return true;
            }

            // Fallback verify if package/update was actually removed even if broadcast didn't arrive in time
            if (flags == 0) {
                return !hasUpdatedSystemAppFlag(context, packageName);
            }
            return !isPackageInstalled(context, packageName);
        } finally {
            try {
                context.unregisterReceiver(receiver);
            } catch (Exception ignored) {}
        }
    }

    private static Object[] buildUninstallArgs(
            Method method,
            String packageName,
            String callerPackageName,
            int flags,
            IntentSender statusReceiver,
            int userId) throws Exception {

        Class<?>[] paramTypes = method.getParameterTypes();
        Object[] args = new Object[paramTypes.length];

        if (paramTypes.length >= 5) {
            // First argument: VersionedPackage (API 26+) or String (API 21-25)
            if (paramTypes[0].getName().equals("android.content.pm.VersionedPackage")) {
                Class<?> vpClass = Class.forName("android.content.pm.VersionedPackage");
                Object versionedPackage = null;
                try {
                    Constructor<?> ctor = vpClass.getConstructor(String.class, int.class);
                    versionedPackage = ctor.newInstance(packageName, -1);
                } catch (NoSuchMethodException e) {
                    Constructor<?> ctor = vpClass.getConstructor(String.class, long.class);
                    versionedPackage = ctor.newInstance(packageName, -1L);
                }
                args[0] = versionedPackage;
            } else {
                args[0] = packageName;
            }

            // Second argument: callerPackageName
            args[1] = callerPackageName;

            // Third argument: flags
            args[2] = flags;

            // Fourth argument: statusReceiver
            args[3] = statusReceiver;

            // Fifth argument: userId
            args[4] = userId;
        } else {
            throw new NoSuchMethodException("Unexpected uninstall method signature with " + paramTypes.length + " parameters");
        }

        return args;
    }

    private static synchronized Object getOrCreatePackageInstallerProxy() throws Exception {
        if (sCachedPackageInstallerProxy != null) {
            return sCachedPackageInstallerProxy;
        }

        IBinder packageBinder = SystemServiceHelper.getSystemService("package");
        if (packageBinder == null) {
            throw new IllegalStateException("Failed to obtain system service: package");
        }

        Class<?> pmStubClass = Class.forName("android.content.pm.IPackageManager$Stub");
        Method asInterface = pmStubClass.getMethod("asInterface", IBinder.class);
        Object pmProxy = asInterface.invoke(null, new ShizukuBinderWrapper(packageBinder));
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
        Object installerProxy = installerAsInterface.invoke(null, new ShizukuBinderWrapper(installerRawBinder));

        sCachedPackageInstallerProxy = installerProxy;
        return installerProxy;
    }

    private static synchronized Method getOrCreateUninstallMethod(Object installerProxy) throws Exception {
        if (sCachedUninstallMethod != null) {
            return sCachedUninstallMethod;
        }

        for (Method m : installerProxy.getClass().getMethods()) {
            if ("uninstall".equals(m.getName()) && m.getParameterTypes().length >= 5) {
                sCachedUninstallMethod = m;
                return m;
            }
        }

        throw new NoSuchMethodException("Could not find uninstall method on IPackageInstaller");
    }

    public static synchronized void invalidateCache() {
        sCachedPackageInstallerProxy = null;
        sCachedUninstallMethod = null;
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
