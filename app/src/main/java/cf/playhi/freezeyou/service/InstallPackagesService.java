package cf.playhi.freezeyou.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcelable;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Date;

import cf.playhi.freezeyou.MainApplication;
import cf.playhi.freezeyou.R;
import cf.playhi.freezeyou.app.FreezeYouBaseService;
import cf.playhi.freezeyou.receiver.InstallPackagesFinishedReceiver;
import cf.playhi.freezeyou.utils.AndroidUserUtils;
import cf.playhi.freezeyou.utils.ApplicationInfoUtils;
import cf.playhi.freezeyou.utils.DebugModeUtils;
import cf.playhi.freezeyou.utils.DevicePolicyManagerUtils;
import cf.playhi.freezeyou.utils.FileUtils;
import cf.playhi.freezeyou.utils.InstallPackagesUtils;
import cf.playhi.freezeyou.utils.PrivilegedShellUtils;
import cf.playhi.freezeyou.utils.RestoreUtils;
import cf.playhi.freezeyou.utils.ShizukuPackageInstaller;
import cf.playhi.freezeyou.utils.UninstallPolicyUtils;

import static cf.playhi.freezeyou.storage.key.DefaultMultiProcessMMKVStorageBooleanKeys.tryDelApkAfterInstalled;
import static cf.playhi.freezeyou.utils.ApplicationIconUtils.getApplicationIcon;
import static cf.playhi.freezeyou.utils.ApplicationIconUtils.getBitmapFromDrawable;
import static cf.playhi.freezeyou.utils.ApplicationLabelUtils.getApplicationLabel;
import static cf.playhi.freezeyou.utils.ToastUtils.showToast;

// Install and uninstall
public class InstallPackagesService extends FreezeYouBaseService {

    public static final String NOTIFICATION_CHANNEL_RESULTS = "InstallPackagesResults";

    public static final long INSTALL_TIMEOUT_MS = 120_000L;
    public static final long UNINSTALL_TIMEOUT_MS = 60_000L;

    private final InstallRequestQueue installRequestQueue = new InstallRequestQueue();

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            stopSelf(startId);
            return super.onStartCommand(null, flags, startId);
        }

        final Intent i = new Intent(intent);
        i.putExtra("requestTime", new Date().getTime());
        final Parcelable packageInfoParcelable = i.getParcelableExtra("packageInfo");
        final PackageInfo packageInfo =
                packageInfoParcelable instanceof PackageInfo
                        ? (PackageInfo) packageInfoParcelable : null;
        if (i.getBooleanExtra("waitForLeaving", false)
                && packageInfo != null
                && packageInfo.packageName != null
                && packageInfo.packageName.equals(MainApplication.getCurrentPackage())) {
            MainApplication.setWaitingForLeavingToInstallApplicationIntent(i);
            // Before the card is posted: a process killed after it appears must still leave a
            // request behind, otherwise the user sees a waiting card for an install that can
            // never happen.
            InstallPackagesUtils.saveWaitingForLeavingRequest(i, packageInfo.packageName);
            InstallPackagesUtils.
                    postWaitingForLeavingToInstallApplicationNotification(this, packageInfo);
            int stopId = installRequestQueue.idleStopId(startId);
            if (stopId >= 0) stopSelf(stopId);
        } else if (installRequestQueue.enqueue(i, startId)) {
            new Thread(this::processQueue).start();
        }
        return super.onStartCommand(i, flags, startId);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(
                        new NotificationChannel("InstallPackages",
                                getString(R.string.installAndUninstall), NotificationManager.IMPORTANCE_NONE)
                );
                // Results and waiting cards are user-visible, and on API 26+ a notification on an
                // IMPORTANCE_NONE channel is never displayed; only the service's own foreground
                // card stays silent.
                notificationManager.createNotificationChannel(
                        new NotificationChannel(NOTIFICATION_CHANNEL_RESULTS,
                                getString(R.string.installAndUninstall), NotificationManager.IMPORTANCE_LOW)
                );
            }
            Notification.Builder mBuilder =
                    new Notification.Builder(this, "InstallPackages");
            mBuilder.setSmallIcon(R.drawable.ic_notification);
            mBuilder.setContentText(getString(R.string.installAndUninstall));
            startForeground(5, mBuilder.build());
        } else {
            Notification.Builder mBuilder = new Notification.Builder(this);
            mBuilder.setSmallIcon(R.drawable.ic_notification);
            mBuilder.setContentText(getString(R.string.installAndUninstall));
            startForeground(5, mBuilder.build());
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void processQueue() {
        while (true) {
            Intent next = installRequestQueue.takeNextOrNull();
            if (next != null) {
                installAndUninstall(next);
                continue;
            }
            int stopId = installRequestQueue.markDrainedIfEmpty();
            if (stopId >= 0) {
                stopSelf(stopId);
                return;
            }
        }
    }

    private void installAndUninstall(Intent intent) {
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26 ?
                new Notification.Builder(this, NOTIFICATION_CHANNEL_RESULTS) :
                new Notification.Builder(this);
        builder.setSmallIcon(R.drawable.ic_notification);

        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        if (intent.getBooleanExtra("install", true)) {//Install
            install(intent, builder, notificationManager);
        } else {//Uninstall
            uninstall(intent, builder, notificationManager);
        }
    }

    private void uninstall(Intent intent, Notification.Builder builder, NotificationManager notificationManager) {

        final Uri packageUri = intent.getParcelableExtra("packageUri");
        String packageName = packageUri == null ? null : packageUri.getEncodedSchemeSpecificPart();
        try {
            if (packageUri == null || !"package".equals(packageUri.getScheme())
                    || packageName == null || packageName.isEmpty()) {
                new Handler(Looper.getMainLooper()).post(() ->
                        showToast(getApplicationContext(),
                                getString(R.string.invalidArguments) + " " + packageUri));
                return;
            }
            final String willBeUninstalledName =
                    getApplicationLabel(this, null, null, packageName);

            Drawable willBeUninstalledIcon =
                    getApplicationIcon(
                            this,
                            packageName,
                            ApplicationInfoUtils.getApplicationInfoFromPkgName(packageName, this),
                            false);

            builder.setContentTitle(String.format(getString(R.string.uninstalling_app), willBeUninstalledName));
            builder.setLargeIcon(getBitmapFromDrawable(willBeUninstalledIcon));
            notificationManager.notify(
                    (packageName + "@InstallPackagesNotification").hashCode(),
                    builder.build()
            );

            boolean uninstallUpdatesOnly = intent.getBooleanExtra("uninstall_updates_only", false);
            // Re-read package state in the service instead of trusting a potentially stale UI
            // extra; these flags decide whether the shell operation affects one user or all.
            PackageInfo packageInfo = getPackageManager().getPackageInfo(
                    packageName, PackageManager.GET_UNINSTALLED_PACKAGES);
            if (packageInfo.applicationInfo == null
                    || RestoreUtils.isAppUninstalled(packageInfo.applicationInfo)) {
                throw new IllegalArgumentException(getString(R.string.invalidArguments));
            }
            boolean isSystemApp = (packageInfo.applicationInfo.flags
                    & android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0;
            boolean hasSystemUpdate = (packageInfo.applicationInfo.flags
                    & android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;

            if (!UninstallPolicyUtils.isValidUpdatesOnlyRequest(
                    isSystemApp, hasSystemUpdate, uninstallUpdatesOnly)) {
                throw new IllegalArgumentException(getString(R.string.invalidArguments));
            }

            long opStartTime = System.currentTimeMillis();
            boolean debug = DebugModeUtils.isDebugModeEnabled();
            if (debug) {
                Log.d("FreezeYouDebug", "InstallPackagesService: starting uninstall for " + packageName
                        + " (updatesOnly=" + uninstallUpdatesOnly + ", isSystem=" + isSystemApp
                        + ", hasUpdate=" + hasSystemUpdate + ")");
            }

            java.util.List<String> commands = UninstallPolicyUtils.buildUninstallCommands(
                    isSystemApp,
                    hasSystemUpdate,
                    uninstallUpdatesOnly,
                    AndroidUserUtils.currentUserId(),
                    packageName);

            boolean shizukuAvailable = PrivilegedShellUtils.isShizukuAvailable();
            boolean deviceOwnerAvailable = Build.VERSION.SDK_INT >= 21
                    && DevicePolicyManagerUtils.isDeviceOwner(this);
            boolean fullUninstallNeedsShell = UninstallPolicyUtils
                    .requiresPrivilegedShellForFullUninstall(
                            isSystemApp, uninstallUpdatesOnly);
            boolean rootCheckRequired = UninstallPolicyUtils.shouldCheckRootPermission(
                    deviceOwnerAvailable, fullUninstallNeedsShell);
            boolean rootAvailable = !shizukuAvailable && rootCheckRequired
                    && cf.playhi.freezeyou.utils.FUFUtils.checkRootPermission();

            if (shizukuAvailable) {
                boolean binderSuccess = false;
                try {
                    binderSuccess = ShizukuPackageInstaller.uninstall(
                            this, packageName, isSystemApp, hasSystemUpdate, uninstallUpdatesOnly);
                } catch (Throwable t) {
                    if (debug) {
                        Log.w("FreezeYouDebug", "InstallPackagesService: Shizuku Binder IPC uninstall failed: "
                                + t.getMessage(), t);
                    }
                }

                if (binderSuccess) {
                    if (debug) {
                        Log.d("FreezeYouDebug", "InstallPackagesService: successfully uninstalled " + packageName
                                + " via Shizuku Binder IPC in " + (System.currentTimeMillis() - opStartTime) + " ms");
                    }
                    onUninstallSuccess(packageName, willBeUninstalledName, uninstallUpdatesOnly, builder, notificationManager);
                    return;
                }

                if (debug) {
                    Log.d("FreezeYouDebug", "InstallPackagesService: falling back to Shizuku shell commands");
                }
                for (String command : commands) {
                    PrivilegedShellUtils.CommandResult result = PrivilegedShellUtils.runShizukuCommand(command, UNINSTALL_TIMEOUT_MS);
                    if (!result.isSuccessful()) {
                        String details = result.output == null || result.output.isEmpty()
                                ? "exit code " + result.exitCode : result.output;
                        throw new Exception(details);
                    }
                }

                if (debug) {
                    Log.d("FreezeYouDebug", "InstallPackagesService: successfully uninstalled " + packageName
                            + " via Shizuku shell in " + (System.currentTimeMillis() - opStartTime) + " ms");
                }
                onUninstallSuccess(packageName, willBeUninstalledName, uninstallUpdatesOnly, builder, notificationManager);
                return;
            } else if (rootAvailable) {
                for (String command : commands) {
                    PrivilegedShellUtils.CommandResult result = PrivilegedShellUtils.runRootCommand(command, UNINSTALL_TIMEOUT_MS);
                    if (!result.isSuccessful()) {
                        String details = result.output == null || result.output.isEmpty()
                                ? "exit code " + result.exitCode : result.output;
                        throw new Exception(details);
                    }
                }

                if (debug) {
                    Log.d("FreezeYouDebug", "InstallPackagesService: successfully uninstalled " + packageName
                            + " via root shell in " + (System.currentTimeMillis() - opStartTime) + " ms");
                }
                onUninstallSuccess(packageName, willBeUninstalledName, uninstallUpdatesOnly, builder, notificationManager);
                return;
            }

            if (deviceOwnerAvailable) {
                // PackageInstaller can silently uninstall ordinary user packages as Device Owner.
                // In Android (DeletePackageHelper.mayDeletePackageLocked), it lacks DELETE_SYSTEM_APP
                // and fails on factory preinstalled system apps, and cannot express the two-step removal
                // for updated system apps. Full removal of any system app therefore requires a privileged shell.
                if (fullUninstallNeedsShell) {
                    throw new IllegalStateException(
                            getString(R.string.full_system_uninstall_requires_shell));
                }
                getPackageManager().getPackageInstaller().uninstall(packageName,
                        PendingIntent.getBroadcast(this, packageName.hashCode(),
                                        new Intent(this, InstallPackagesFinishedReceiver.class)
                                                .putExtra("name", willBeUninstalledName)
                                                .putExtra("pkgName", packageName)
                                                .putExtra("install", false),
                                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                                                ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE
                                                : PendingIntent.FLAG_UPDATE_CURRENT)
                                .getIntentSender());
                return;
            }

            throw new Exception("No sufficient privileges to uninstall.");
        } catch (final Exception e) {
            e.printStackTrace();
            if (DebugModeUtils.isDebugModeEnabled()) {
                Log.e("FreezeYouDebug", "InstallPackagesService: uninstall failed for " + packageName + ": " + e.getMessage(), e);
            }
            InstallPackagesUtils
                    .notifyFinishNotification(
                            this, notificationManager, builder,
                            false,
                            packageName,
                            getString(R.string.uninstallFailed),
                            e.getLocalizedMessage(),
                            false);
            new Handler(Looper.getMainLooper()).post(() ->
                    showToast(
                            getApplicationContext(),
                            String.format(getString(R.string.errorUninstallToast),
                                    e.getLocalizedMessage())
                    )
            );
        }
    }

    private void onUninstallSuccess(
            String packageName,
            String willBeUninstalledName,
            boolean uninstallUpdatesOnly,
            Notification.Builder builder,
            NotificationManager notificationManager) {
        InstallPackagesUtils.notifyFinishNotification(
                this, notificationManager, builder,
                false,
                packageName,
                String.format(getString(uninstallUpdatesOnly
                        ? R.string.updates_uninstallFinished
                        : R.string.app_uninstallFinished), willBeUninstalledName),
                null,
                true);
        File cacheIcon = new File(getCacheDir() + "/icon/" + packageName + ".png");
        if (cacheIcon.exists()) {
            cacheIcon.delete();
        }
        File filesIcon = new File(getFilesDir() + "/icon/" + packageName + ".png");
        if (filesIcon.exists()) {
            filesIcon.delete();
        }

        sendBroadcast(new Intent("cf.playhi.freezeyou.action.packageStatusChanged")
                .putExtra("pkgName", packageName)
                .putExtra("refreshAppList", !uninstallUpdatesOnly)
                .putExtra("uninstallSuccess", true)
                .putExtra("appName", willBeUninstalledName)
                .putExtra("uninstallUpdatesOnly", uninstallUpdatesOnly));
    }

    private void install(Intent intent, Notification.Builder builder, NotificationManager notificationManager) {
        try {
            String apkFilePath = intent.getStringExtra("apkFilePath");
            if (apkFilePath == null || "".equals(apkFilePath) || !new File(apkFilePath).exists()) {
                Uri packageUri = intent.getParcelableExtra("packageUri");
                if (packageUri == null) return;
                InputStream in = getContentResolver().openInputStream(packageUri);
                if (in == null) {
                    return;
                }

                String apkFileName = "package" + new Date().getTime() + "F.apk";
                apkFilePath = getExternalCacheDir() + File.separator + "ZDF-" + apkFileName;

                FileUtils.copyFile(in, apkFilePath);
            }

            PackageManager pm = getPackageManager();
            PackageInfo packageInfo = pm.getPackageArchiveInfo(apkFilePath, 0);
            if (packageInfo == null) return;
            String willBeInstalledPackageName = packageInfo.packageName;
            packageInfo.applicationInfo.sourceDir = apkFilePath;
            packageInfo.applicationInfo.publicSourceDir = apkFilePath;
            String willBeInstalledName = pm.getApplicationLabel(packageInfo.applicationInfo).toString();
            Drawable willBeInstalledIcon = pm.getApplicationIcon(packageInfo.applicationInfo);

            builder.setContentTitle(String.format(getString(R.string.installing_app), willBeInstalledName));
            builder.setProgress(100, 0, true);
            builder.setLargeIcon(getBitmapFromDrawable(willBeInstalledIcon));
            notificationManager.notify(
                    (willBeInstalledPackageName + "@InstallPackagesNotification").hashCode(),
                    builder.build()
            );

            if (Build.VERSION.SDK_INT >= 21 && DevicePolicyManagerUtils.isDeviceOwner(this)) {
                PackageInstaller packageInstaller = pm.getPackageInstaller();
                PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
                        PackageInstaller.SessionParams.MODE_FULL_INSTALL);
                params.setAppPackageName(willBeInstalledPackageName);
                int sessionId = packageInstaller.createSession(params);
                PackageInstaller.Session session = packageInstaller.openSession(sessionId);
                OutputStream outputStream = session.openWrite(
                        Integer.toString(apkFilePath.hashCode()), 0, -1);
                InputStream in1 = new FileInputStream(apkFilePath);
                byte[] buffer = new byte[1024 * 1024];
                int bytesRead;
                while ((bytesRead = in1.read(buffer)) >= 0) {
                    outputStream.write(buffer, 0, bytesRead);
                }
                in1.close();
                session.fsync(outputStream);
                outputStream.close();
                session.commit(
                        PendingIntent.getBroadcast(this, sessionId,
                                        new Intent(
                                                this,
                                                InstallPackagesFinishedReceiver.class)
                                                .putExtra("name", willBeInstalledName)
                                                .putExtra("pkgName", willBeInstalledPackageName)
                                                .putExtra("apkFilePath", apkFilePath),
                                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                                                ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE
                                                : PendingIntent.FLAG_UPDATE_CURRENT)
                                .getIntentSender());
            } else {
                // Root Mode
                String result = null;
                try {
                    String cmd = "pm install -r " + PrivilegedShellUtils.shellQuote(apkFilePath);
                    PrivilegedShellUtils.CommandResult cmdRes =
                            PrivilegedShellUtils.runRootCommand(cmd, INSTALL_TIMEOUT_MS);
                    result = cmdRes.output;
                } finally {
                    // A command killed by its deadline never returns, so the staged ZDF- copy
                    // has to be cleaned up here and not only after a normal return.
                    InstallPackagesUtils.deleteTempFile(this, apkFilePath, false);
                    if (result != null && result.toLowerCase().contains("success")) {
                        InstallPackagesUtils
                                .notifyFinishNotification(
                                        this, notificationManager, builder,
                                        true,
                                        willBeInstalledPackageName,
                                        String.format(
                                                getString(R.string.app_installFinished),
                                                willBeInstalledName),
                                        null,
                                        true);
                        if (tryDelApkAfterInstalled.getValue(null))
                            InstallPackagesUtils.deleteTempFile(this, apkFilePath, true);
                    } else {
                        InstallPackagesUtils
                                .notifyFinishNotification(
                                        this, notificationManager, builder,
                                        true,
                                        willBeInstalledPackageName,
                                        String.format(
                                                getString(R.string.app_installFailed),
                                                willBeInstalledName),
                                        String.format(getString(R.string.reason_colon), result),
                                        false);
                    }
                }
            }
        } catch (final Exception e) {
            e.printStackTrace();
            InstallPackagesUtils
                    .notifyFinishNotification(
                            this, notificationManager, builder,
                            true,
                            null,
                            getString(R.string.installFailed),
                            e.getLocalizedMessage(),
                            false);
            new Handler(Looper.getMainLooper()).post(() ->
                    showToast(
                            getApplicationContext(),
                            String.format(getString(R.string.errorInstallToast),
                                    e.getLocalizedMessage())
                    )
            );

        }

    }

    // Enqueue and drain share one monitor: a request cannot be queued after the worker decided to
    // stop, and stopSelf always receives the newest startId so a newer command keeps the service.
    static final class InstallRequestQueue {

        private final ArrayList<Intent> pending = new ArrayList<>();
        private boolean processing;
        private int lastStartId = -1;

        synchronized boolean enqueue(Intent intent, int startId) {
            lastStartId = startId;
            pending.add(intent);
            if (processing) {
                return false;
            }
            processing = true;
            return true;
        }

        synchronized Intent takeNextOrNull() {
            return pending.isEmpty() ? null : pending.remove(0);
        }

        synchronized int markDrainedIfEmpty() {
            if (!pending.isEmpty()) {
                return -1;
            }
            processing = false;
            return lastStartId;
        }

        synchronized int idleStopId(int startId) {
            lastStartId = startId;
            return processing || !pending.isEmpty() ? -1 : lastStartId;
        }
    }

}
