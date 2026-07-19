package cf.playhi.freezeyou.utils

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.os.Build
import cf.playhi.freezeyou.R
import cf.playhi.freezeyou.ui.ShowSimpleDialogActivity
import java.io.File

object InstallPackagesUtils {
    @JvmStatic
    fun notifyFinishNotification(
        context: Context, notificationManager: NotificationManager,
        builder: Notification.Builder, install: Boolean,
        beOperatedPackageName: String?,
        title: String?, text: String?, success: Boolean
    ) {
        if (beOperatedPackageName == null) return  // null 无法取得通知特征 hashcode

        // 小图标
        builder.setSmallIcon(R.drawable.ic_notification)
        builder.setProgress(0, 0, false)
        if (install) {

            // 提示标题
            if (title != null) builder.setContentTitle(title)

            // 提示文本
            if (text != null) builder.setContentText(text)
            if (success) {

                // 大图标
                builder.setLargeIcon(
                    ApplicationIconUtils.getBitmapFromDrawable(
                        ApplicationIconUtils.getApplicationIcon(
                            context,
                            beOperatedPackageName,
                            ApplicationInfoUtils
                                .getApplicationInfoFromPkgName(
                                    beOperatedPackageName,
                                    context
                                ),
                            false
                        )
                    )
                )

                // 点击打开
                val resultIntent =
                    context.packageManager.getLaunchIntentForPackage(beOperatedPackageName)
                if (resultIntent != null) {
                    val resultPendingIntent = PendingIntent
                        .getActivity(
                            context,
                            (beOperatedPackageName + "@InstallPackagesNotification").hashCode(),
                            resultIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                    builder.setContentIntent(resultPendingIntent)
                    builder.setAutoCancel(true)
                    if (text == null) builder.setContentText(context.getString(R.string.openImmediately))
                }
            } else {
                // 错误信息弹窗
                val resultPendingIntent = PendingIntent
                    .getActivity(
                        context,
                        (beOperatedPackageName + "@InstallPackagesNotification").hashCode(),
                        Intent(context, ShowSimpleDialogActivity::class.java)
                            .putExtra("title", title)
                            .putExtra("text", text),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                builder.setContentIntent(resultPendingIntent)
            }
        } else {
            if (text != null) builder.setContentText(text)
            if (title != null) builder.setContentTitle(title)
            val resultPendingIntent = PendingIntent
                .getActivity(
                    context,
                    (beOperatedPackageName + "@InstallPackagesNotification").hashCode(),
                    Intent(context, ShowSimpleDialogActivity::class.java)
                        .putExtra("title", title)
                        .putExtra("text", text),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            builder.setContentIntent(resultPendingIntent)
        }
        notificationManager.notify(
            (beOperatedPackageName + "@InstallPackagesNotification").hashCode(), builder.build()
        )
    }

    /**
     * @param context     Context
     * @param apkFilePath 文件路径
     * @param noCheck     不检查是否为安装过程生成的，直接删除
     */
    @JvmStatic
    fun deleteTempFile(context: Context, apkFilePath: String, noCheck: Boolean) {
        if (noCheck || apkFilePath.startsWith(context.externalCacheDir.toString() + File.separator + "ZDF-")) {
            val file = File(apkFilePath)
            if (file.exists()) {
                file.delete()
            }
        }
    }

    @JvmStatic
    fun postWaitingForLeavingToInstallApplicationNotification(
        context: Context,
        packageInfo: PackageInfo
    ) {
        val applicationInfo = packageInfo.applicationInfo ?: return
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(
            context,
            "InstallPackages"
        ) else Notification.Builder(context)
        builder.setSmallIcon(R.drawable.ic_notification)
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager?
                ?: return
        val pm = context.packageManager
        builder.setContentTitle(
            String.format(
                context.getString(R.string.waitingToInstall_app),
                pm.getApplicationLabel(applicationInfo)
            )
        )
        builder.setProgress(100, 0, true)
        builder.setLargeIcon(
            ApplicationIconUtils.getBitmapFromDrawable(
                pm.getApplicationIcon(applicationInfo)
            )
        )
        notificationManager.notify(
            (packageInfo.packageName + "@InstallPackagesNotification").hashCode(),
            builder.build()
        )
    }
}
