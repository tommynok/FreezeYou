package cf.playhi.freezeyou.utils

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import cf.playhi.freezeyou.Freeze
import cf.playhi.freezeyou.R
import cf.playhi.freezeyou.receiver.NotificationDeletedReceiver
import cf.playhi.freezeyou.service.FUFService
import cf.playhi.freezeyou.storage.key.DefaultMultiProcessMMKVStorageBooleanKeys.*
import cf.playhi.freezeyou.utils.ApplicationLabelUtils.getApplicationLabel
import cf.playhi.freezeyou.utils.ToastUtils.showToast
import net.grandcentrix.tray.AppPreferences

object NotificationUtils {
    @JvmStatic
    fun createFUFQuickNotification(
        context: Context,
        pkgName: String,
        iconResId: Int,
        bitmap: Bitmap?
    ) {
        val freezeImmediately = notificationBarFreezeImmediately.getValue(null)
        val description = if (freezeImmediately) context.getString(R.string.freezeImmediately) else context.getString(R.string.disableAEnable)
        val mBuilder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(context, "FAUf") else Notification.Builder(context)
        val mId = pkgName.hashCode()
        val name = getApplicationLabel(context, null, null, pkgName)
        if (context.getString(R.string.uninstalled) != name) {
            mBuilder.setSmallIcon(iconResId)
            mBuilder.setLargeIcon(bitmap)
            mBuilder.setContentTitle(name)
            mBuilder.setContentText(description)
            mBuilder.setAutoCancel(!notificationBarDisableClickDisappear.getValue(null))
            mBuilder.setOngoing(notificationBarDisableSlideOut.getValue(null))
            val intent = Intent(context, NotificationDeletedReceiver::class.java).putExtra("pkgName", pkgName)
            val pendingIntent = PendingIntent.getBroadcast(
                context, mId, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            mBuilder.setDeleteIntent(pendingIntent)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val importance = NotificationManager.IMPORTANCE_LOW
                val channel = NotificationChannel("FAUf", description, importance)
                channel.description = description
                // Register the channel with the system; you can't change the importance
                // or other notification behaviors after this
                val notificationManager = context.getSystemService(NotificationManager::class.java)
                notificationManager?.createNotificationChannel(channel)
            }
            // Create an Intent for the activity you want to start
            val resultIntent: Intent
            val resultPendingIntent: PendingIntent
            if (freezeImmediately) {
                resultIntent = Intent(context, FUFService::class.java)
                    .putExtra("pkgName", pkgName)
                    .putExtra("single", true)
                    .putExtra("freeze", true)
                resultPendingIntent = PendingIntent.getService(
                    context, mId, resultIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            } else {
                resultIntent = Intent(context, Freeze::class.java).putExtra("pkgName", pkgName).putExtra("fromShortcut", false)
                resultPendingIntent = PendingIntent.getActivity(
                    context, mId, resultIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            }
            mBuilder.setContentIntent(resultPendingIntent)
            val mNotificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager?
            if (mNotificationManager != null) {
                mNotificationManager.notify(mId, mBuilder.build())
                val appPreferences = AppPreferences(context)
                val notifying = appPreferences.getString("notifying", "")
                if (notifying != null && !notifying.contains("$pkgName,")) {
                    appPreferences.put("notifying", notifying + pkgName + ",")
                }
            }
        }
    }

    @JvmStatic
    fun deleteNotification(context: Context, pkgName: String) {
        val mNotificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager?
        if (mNotificationManager != null) {
            mNotificationManager.cancel(pkgName.hashCode())
            deleteNotifying(context, pkgName)
        }
    }

    private fun deleteNotifying(context: Context, pkgName: String): Boolean {
        val defaultSharedPreferences = AppPreferences(context)
        val notifying = defaultSharedPreferences.getString("notifying", "")
        return notifying == null || !notifying.contains("$pkgName,") || defaultSharedPreferences.put("notifying", notifying.replace("$pkgName,", ""))
    }

    @JvmStatic
    fun startAppNotificationSettingsSystemActivity(activity: Activity, pkgName: String?, pkgUid: Int) {
        val intent = Intent("android.settings.APP_NOTIFICATION_SETTINGS")
        intent.putExtra("app_package", pkgName)
        intent.putExtra("app_uid", pkgUid)
        intent.putExtra("android.provider.extra.APP_PACKAGE", pkgName)
        try {
            activity.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            showToast(activity, e.localizedMessage)
        }
    }
}
