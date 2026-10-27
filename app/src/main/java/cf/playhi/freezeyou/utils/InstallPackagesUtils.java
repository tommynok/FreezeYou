package cf.playhi.freezeyou.utils;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;

import java.io.File;
import java.util.UUID;

import cf.playhi.freezeyou.R;
import cf.playhi.freezeyou.service.InstallPackagesService;
import cf.playhi.freezeyou.storage.mmkv.DefaultMultiProcessMMKVStorage;
import cf.playhi.freezeyou.ui.ShowSimpleDialogActivity;

public final class InstallPackagesUtils {

    private static final String KEY_WAITING_FOR_LEAVING_REQUEST =
            "runtimeWaitingForLeavingInstallRequest";
    private static final String WAITING_REQUEST_VERSION = "2";
    // A package name, a path and a URI cannot contain NUL, so it separates the fields without
    // an escaping layer of its own.
    private static final String WAITING_REQUEST_SEPARATOR = "\u0000";

    public static void notifyFinishNotification(
            Context context, NotificationManager notificationManager,
            Notification.Builder builder, boolean install,
            String beOperatedPackageName,
            String title, String text, boolean success) {

        if (beOperatedPackageName == null)
            return; // null package cannot produce notification tag hashcode

        // Small icon
        builder.setSmallIcon(R.drawable.ic_notification);

        builder.setProgress(0, 0, false);

        if (install) {

            // Content title
            if (title != null)
                builder.setContentTitle(title);

            // Content text
            if (text != null)
                builder.setContentText(text);

            if (success) {

                // Large icon
                builder.setLargeIcon(
                        ApplicationIconUtils.getBitmapFromDrawable(
                                ApplicationIconUtils.getApplicationIcon(
                                        context,
                                        beOperatedPackageName,
                                        ApplicationInfoUtils
                                                .getApplicationInfoFromPkgName(
                                                        beOperatedPackageName,
                                                        context),
                                        false)
                        )
                );

                // Launch on click
                Intent resultIntent =
                        context.getPackageManager().getLaunchIntentForPackage(beOperatedPackageName);
                if (resultIntent != null) {
                    PendingIntent resultPendingIntent =
                            PendingIntent
                                    .getActivity(
                                            context,
                                            (beOperatedPackageName + "@InstallPackagesNotification").hashCode(),
                                            resultIntent,
                                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                                                    ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                                                    : PendingIntent.FLAG_UPDATE_CURRENT);
                    builder.setContentIntent(resultPendingIntent);
                    builder.setAutoCancel(true);

                    if (text == null)
                        builder.setContentText(context.getString(R.string.openImmediately));

                }
            } else {
                // Error dialog
                PendingIntent resultPendingIntent =
                        PendingIntent
                                .getActivity(
                                        context,
                                        (beOperatedPackageName + "@InstallPackagesNotification").hashCode(),
                                        new Intent(context, ShowSimpleDialogActivity.class)
                                                .putExtra("title", title)
                                                .putExtra("text", text),
                                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                                                ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                                                : PendingIntent.FLAG_UPDATE_CURRENT);
                builder.setContentIntent(resultPendingIntent);
            }

        } else {

            if (text != null)
                builder.setContentText(text);

            if (title != null)
                builder.setContentTitle(title);

            PendingIntent resultPendingIntent =
                    PendingIntent
                            .getActivity(
                                    context,
                                    (beOperatedPackageName + "@InstallPackagesNotification").hashCode(),
                                    new Intent(context, ShowSimpleDialogActivity.class)
                                            .putExtra("title", title)
                                            .putExtra("text", text),
                                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                                            ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                                            : PendingIntent.FLAG_UPDATE_CURRENT);
            builder.setContentIntent(resultPendingIntent);

        }

        notificationManager.notify(
                (beOperatedPackageName + "@InstallPackagesNotification").hashCode(), builder.build()
        );

    }

    /**
     * @param context     Context
     * @param apkFilePath File path
     * @param noCheck     Delete directly without checking if generated during install
     */
    public static void deleteTempFile(Context context, String apkFilePath, boolean noCheck) {
        if (noCheck || apkFilePath.startsWith(context.getExternalCacheDir() + File.separator + "ZDF-")) {
            File file = new File(apkFilePath);
            if (file.exists()) {
                file.delete();
            }
        }
    }

    public static void postWaitingForLeavingToInstallApplicationNotification(Context context, PackageInfo packageInfo) {

        Notification.Builder builder = Build.VERSION.SDK_INT >= 26 ?
                new Notification.Builder(context, InstallPackagesService.NOTIFICATION_CHANNEL_RESULTS) :
                new Notification.Builder(context);
        builder.setSmallIcon(R.drawable.ic_notification);

        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (notificationManager == null) return;

        PackageManager pm = context.getPackageManager();

        builder.setContentTitle(
                String.format(
                        context.getString(R.string.waitingToInstall_app),
                        pm.getApplicationLabel(packageInfo.applicationInfo)
                )
        );
        builder.setProgress(100, 0, true);
        builder.setLargeIcon(
                ApplicationIconUtils.getBitmapFromDrawable(
                        pm.getApplicationIcon(packageInfo.applicationInfo)
                )
        );
        notificationManager.notify(
                (packageInfo.packageName + "@InstallPackagesNotification").hashCode(),
                builder.build()
        );

    }

    public static void cancelWaitingForLeavingToInstallApplicationNotification(
            Context context, String packageName) {
        if (packageName == null) return;
        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.cancel(
                    (packageName + "@InstallPackagesNotification").hashCode());
        }
    }

    /**
     * Keeps the pending request where it survives the process: the accessibility service reads it
     * back when the user leaves the target application, possibly after the old process died.
     */
    public static synchronized void saveWaitingForLeavingRequest(
            Intent intent, String targetPackageName) {
        if (intent == null || targetPackageName == null || targetPackageName.isEmpty()) return;
        new DefaultMultiProcessMMKVStorage()
                .putString(
                        KEY_WAITING_FOR_LEAVING_REQUEST,
                        WaitingInstallRequest.fromIntent(intent, targetPackageName).encode())
                .sync();
    }

    public static synchronized void saveAndPostWaitingForLeavingRequest(
            Context context, Intent intent, PackageInfo packageInfo) {
        if (packageInfo == null || packageInfo.packageName == null) return;
        saveWaitingForLeavingRequest(intent, packageInfo.packageName);
        postWaitingForLeavingToInstallApplicationNotification(context, packageInfo);
    }

    public static synchronized WaitingInstallRequest getWaitingForLeavingRequest() {
        return WaitingInstallRequest.decode(
                new DefaultMultiProcessMMKVStorage()
                        .getString(KEY_WAITING_FOR_LEAVING_REQUEST, null));
    }

    /**
     * Drops the request and its card together. Save-and-post runs under the same lock, so a stale
     * cleanup can never cancel the card of a newer request for the same package.
     */
    public static synchronized boolean discardWaitingForLeavingRequest(
            Context context, WaitingInstallRequest expected) {
        if (!clearWaitingForLeavingRequestIfMatches(expected)) return false;
        cancelWaitingForLeavingToInstallApplicationNotification(context, expected.packageName);
        return true;
    }

    /**
     * Removes the stored request only if it is still the one being dispatched. The install service
     * may save a newer request between the start call and this clear; a blind remove would erase it
     * while its waiting card stays on screen.
     */
    public static synchronized boolean clearWaitingForLeavingRequestIfMatches(
            WaitingInstallRequest expected) {
        if (expected == null) return false;
        final DefaultMultiProcessMMKVStorage storage = new DefaultMultiProcessMMKVStorage();
        if (!expected.encode()
                .equals(storage.getString(KEY_WAITING_FOR_LEAVING_REQUEST, null))) {
            return false;
        }
        storage.remove(KEY_WAITING_FOR_LEAVING_REQUEST).sync();
        return true;
    }

    public static final class WaitingInstallRequest {

        public final String requestId;
        public final String packageName;
        public final boolean install;
        public final String apkFilePath;
        public final String packageUri;
        public final boolean uninstallUpdatesOnly;

        public WaitingInstallRequest(
                String packageName, boolean install,
                String apkFilePath, String packageUri,
                boolean uninstallUpdatesOnly) {
            this(null, packageName, install, apkFilePath, packageUri, uninstallUpdatesOnly);
        }

        public WaitingInstallRequest(
                String requestId,
                String packageName, boolean install,
                String apkFilePath, String packageUri,
                boolean uninstallUpdatesOnly) {
            this.requestId = requestId == null || requestId.isEmpty()
                    ? UUID.randomUUID().toString() : requestId;
            this.packageName = packageName;
            this.install = install;
            this.apkFilePath = apkFilePath;
            this.packageUri = packageUri;
            this.uninstallUpdatesOnly = uninstallUpdatesOnly;
        }

        public static WaitingInstallRequest fromIntent(Intent intent, String targetPackageName) {
            Uri packageUri = intent.getParcelableExtra("packageUri");
            return new WaitingInstallRequest(
                    targetPackageName,
                    intent.getBooleanExtra("install", true),
                    intent.getStringExtra("apkFilePath"),
                    packageUri == null ? null : packageUri.toString(),
                    intent.getBooleanExtra("uninstall_updates_only", false));
        }

        public boolean isApkAvailable() {
            if (!install) return true;
            // A content-URI request has no staged path of its own: the service copies it again.
            if (apkFilePath == null || apkFilePath.isEmpty()) return true;
            if (new File(apkFilePath).exists()) return true;
            // The staged copy is gone, but the service has its own URI-copy fallback.
            return packageUri != null;
        }

        public boolean matches(WaitingInstallRequest other) {
            return other != null && requestId.equals(other.requestId);
        }

        public Intent toIntent(Context context) {
            Intent intent = new Intent(context, InstallPackagesService.class)
                    .putExtra("install", install)
                    .putExtra("uninstall_updates_only", uninstallUpdatesOnly);
            if (apkFilePath != null) {
                intent.putExtra("apkFilePath", apkFilePath);
            }
            if (packageUri != null) {
                intent.putExtra("packageUri", Uri.parse(packageUri));
            }
            return intent;
        }

        public String encode() {
            return WAITING_REQUEST_VERSION + WAITING_REQUEST_SEPARATOR
                    + requestId + WAITING_REQUEST_SEPARATOR
                    + packageName + WAITING_REQUEST_SEPARATOR
                    + (install ? "1" : "0") + WAITING_REQUEST_SEPARATOR
                    + (apkFilePath == null ? "" : apkFilePath) + WAITING_REQUEST_SEPARATOR
                    + (packageUri == null ? "" : packageUri) + WAITING_REQUEST_SEPARATOR
                    + (uninstallUpdatesOnly ? "1" : "0");
        }

        public static WaitingInstallRequest decode(String encoded) {
            if (encoded == null) return null;
            String[] fields = encoded.split(WAITING_REQUEST_SEPARATOR, -1);
            if (fields.length != 7
                    || !WAITING_REQUEST_VERSION.equals(fields[0])
                    || fields[1].isEmpty()
                    || fields[2].isEmpty()) {
                return null;
            }
            return new WaitingInstallRequest(
                    fields[1],
                    fields[2],
                    "1".equals(fields[3]),
                    fields[4].isEmpty() ? null : fields[4],
                    fields[5].isEmpty() ? null : fields[5],
                    "1".equals(fields[6]));
        }
    }

}
