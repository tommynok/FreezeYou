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

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Date;

import cf.playhi.freezeyou.MainApplication;
import cf.playhi.freezeyou.R;
import cf.playhi.freezeyou.app.FreezeYouBaseService;
import cf.playhi.freezeyou.receiver.InstallPackagesFinishedReceiver;
import cf.playhi.freezeyou.utils.AndroidUserUtils;
import cf.playhi.freezeyou.utils.ApplicationInfoUtils;
import cf.playhi.freezeyou.utils.DevicePolicyManagerUtils;
import cf.playhi.freezeyou.utils.FileUtils;
import cf.playhi.freezeyou.utils.InstallPackagesUtils;
import cf.playhi.freezeyou.utils.PrivilegedShellUtils;
import cf.playhi.freezeyou.utils.RestoreUtils;
import cf.playhi.freezeyou.utils.UninstallPolicyUtils;

import static cf.playhi.freezeyou.storage.key.DefaultMultiProcessMMKVStorageBooleanKeys.tryDelApkAfterInstalled;
import static cf.playhi.freezeyou.utils.ApplicationIconUtils.getApplicationIcon;
import static cf.playhi.freezeyou.utils.ApplicationIconUtils.getBitmapFromDrawable;
import static cf.playhi.freezeyou.utils.ApplicationLabelUtils.getApplicationLabel;
import static cf.playhi.freezeyou.utils.ProcessUtils.destroyProcess;
import static cf.playhi.freezeyou.utils.ToastUtils.showToast;

// Install and uninstall
public class InstallPackagesService extends FreezeYouBaseService {

    private final ArrayList<Intent> intentArrayList = new ArrayList<>();
    private boolean processing = false;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null)
            return super.onStartCommand(null, flags, startId);

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
            InstallPackagesUtils.
                    postWaitingForLeavingToInstallApplicationNotification(this, packageInfo);
            if (!processing) stopSelf();
        } else {
            if (processing) {
                intentArrayList.add(i);
            } else {
                new Thread(() -> installAndUninstall(i)).start();
            }
        }
        return super.onStartCommand(i, flags, startId);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null)
                notificationManager.createNotificationChannel(
                        new NotificationChannel("InstallPackages",
                                getString(R.string.installAndUninstall), NotificationManager.IMPORTANCE_NONE)
                );
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

    private void installAndUninstall(Intent intent) {
        processing = true;

        Notification.Builder builder = Build.VERSION.SDK_INT >= 26 ?
                new Notification.Builder(this, "InstallPackages") :
                new Notification.Builder(this);
        builder.setSmallIcon(R.drawable.ic_notification);

        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        if (intent.getBooleanExtra("install", true)) {//Install
            install(intent, builder, notificationManager);
        } else {//Uninstall
            uninstall(intent, builder, notificationManager);
        }

        //移除已完成的
        intentArrayList.remove(intent);

        checkIfAllTaskDoneAndStopSelf();
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
            new Handler(Looper.getMainLooper()).post(() ->
                    showToast(getApplicationContext(),
                            String.format(getString(R.string.uninstalling_app), willBeUninstalledName)));

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
                            isSystemApp, hasSystemUpdate, uninstallUpdatesOnly);
            boolean rootCheckRequired = UninstallPolicyUtils.shouldCheckRootPermission(
                    deviceOwnerAvailable, fullUninstallNeedsShell);
            boolean rootAvailable = !shizukuAvailable && rootCheckRequired
                    && cf.playhi.freezeyou.utils.FUFUtils.checkRootPermission();
            if (shizukuAvailable || rootAvailable) {
                for (String command : commands) {
                    PrivilegedShellUtils.CommandResult result = shizukuAvailable
                            ? PrivilegedShellUtils.runShizukuCommand(command)
                            : PrivilegedShellUtils.runRootCommand(command);
                    if (!result.isSuccessful()) {
                        String details = result.output == null || result.output.isEmpty()
                                ? "exit code " + result.exitCode : result.output;
                        throw new Exception(details);
                    }
                }

                InstallPackagesUtils.notifyFinishNotification(
                        this, notificationManager, builder,
                        false,
                        packageName,
                        String.format(getString(R.string.app_uninstallFinished), willBeUninstalledName),
                        null,
                        true);
                final String finishMessage = String.format(
                        getString(R.string.app_uninstallFinished), willBeUninstalledName);
                new Handler(Looper.getMainLooper()).post(() ->
                        showToast(getApplicationContext(), finishMessage));
                sendBroadcast(new Intent("cf.playhi.freezeyou.action.packageStatusChanged")
                        .putExtra("pkgName", packageName)
                        .putExtra("refreshAppList", true));
                return;
            }

            if (deviceOwnerAvailable) {
                // PackageInstaller can silently uninstall ordinary packages as Device Owner.
                // It cannot express the two-step "remove update, then uninstall for the current
                // user" operation, so do not claim a complete removal for an updated system app.
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
                    Process process = Runtime.getRuntime().exec("su");
                    DataOutputStream outputStream = new DataOutputStream(process.getOutputStream());
                    outputStream.writeBytes("pm install -r \"" + apkFilePath + "\"\n");
                    outputStream.writeBytes("exit\n");
                    outputStream.flush();
                    process.waitFor();

                    // Delete Temp File
                    InstallPackagesUtils.deleteTempFile(this, apkFilePath, false);

                    InputStream pi = process.getInputStream();
                    BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(pi));
                    result = bufferedReader.readLine();

                    destroyProcess(outputStream, process);
                } finally {
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

    private void checkIfAllTaskDoneAndStopSelf() {
        if (intentArrayList.isEmpty()) {
            processing = false;
            stopSelf();
        } else {
            installAndUninstall(intentArrayList.get(0));
        }
    }

}
