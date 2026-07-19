package cf.playhi.freezeyou.receiver

import android.app.Notification
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import cf.playhi.freezeyou.R
import cf.playhi.freezeyou.storage.key.DefaultMultiProcessMMKVStorageBooleanKeys.tryDelApkAfterInstalled
import cf.playhi.freezeyou.utils.InstallPackagesUtils

class InstallPackagesFinishedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return
        val installStatus = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)
        val pkgName = intent.getStringExtra("pkgName")
        val name = intent.getStringExtra("name")
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(
            context,
            "InstallPackages"
        ) else Notification.Builder(context)
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager?
                ?: return
        var message: String? = null
        if (installStatus != PackageInstaller.STATUS_SUCCESS) {
            message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
            if (message.isNullOrEmpty()) {
                message = context.getString(R.string.unknown)
            }
            message = String.format(context.getString(R.string.reason_colon), message)
        }
        if (intent.getBooleanExtra("install", true)) {
            val apkFilePath = intent.getStringExtra("apkFilePath") ?: ""

            // Delete Temp File
            InstallPackagesUtils.deleteTempFile(context, apkFilePath, false)
            if (installStatus == PackageInstaller.STATUS_SUCCESS) {
                InstallPackagesUtils
                    .notifyFinishNotification(
                        context, notificationManager, builder,
                        true,
                        pkgName,
                        String.format(context.getString(R.string.app_installFinished), name),
                        null,
                        true
                    )
                if (tryDelApkAfterInstalled.getValue(null)) {
                    InstallPackagesUtils.deleteTempFile(context, apkFilePath, true)
                }
            } else {
                InstallPackagesUtils
                    .notifyFinishNotification(
                        context, notificationManager, builder,
                        true,
                        pkgName,
                        String.format(context.getString(R.string.app_installFailed), name),
                        message,
                        false
                    )
            }
        } else {
            if (installStatus == PackageInstaller.STATUS_SUCCESS) {
                InstallPackagesUtils
                    .notifyFinishNotification(
                        context, notificationManager, builder,
                        true,
                        pkgName,
                        String.format(context.getString(R.string.app_uninstallFinished), name),
                        null,
                        true
                    )
            } else {
                InstallPackagesUtils
                    .notifyFinishNotification(
                        context, notificationManager, builder,
                        true,
                        pkgName,
                        String.format(context.getString(R.string.app_uninstallFailed), name),
                        message,
                        false
                    )
            }
        }
    }
}
