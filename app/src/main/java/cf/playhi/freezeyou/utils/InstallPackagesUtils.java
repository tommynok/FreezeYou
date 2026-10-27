package cf.playhi.freezeyou.utils;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;

import java.io.File;

import cf.playhi.freezeyou.R;
import cf.playhi.freezeyou.service.InstallPackagesService;
import cf.playhi.freezeyou.ui.ShowSimpleDialogActivity;

public final class InstallPackagesUtils {

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

}
