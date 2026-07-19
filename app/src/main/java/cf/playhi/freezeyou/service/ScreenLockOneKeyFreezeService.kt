package cf.playhi.freezeyou.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.IBinder
import cf.playhi.freezeyou.Main
import cf.playhi.freezeyou.R
import cf.playhi.freezeyou.app.FreezeYouBaseService
import cf.playhi.freezeyou.listener.ScreenLockListener
import cf.playhi.freezeyou.storage.key.DefaultMultiProcessMMKVStorageBooleanKeys.useForegroundService

class ScreenLockOneKeyFreezeService : FreezeYouBaseService() {
    private var screenLockListener: ScreenLockListener? = null
    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
            || useForegroundService.getValue(null)
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val notificationManager = getSystemService(NotificationManager::class.java)
                notificationManager?.createNotificationChannel(
                    NotificationChannel(
                        "BackgroundService", getString(R.string.backgroundService),
                        NotificationManager.IMPORTANCE_NONE
                    )
                )
                val mBuilder = Notification.Builder(this, "BackgroundService")
                mBuilder.setSmallIcon(R.drawable.ic_notification)
                mBuilder.setContentText(getString(R.string.backgroundService))
                val resultIntent = Intent(applicationContext, Main::class.java)
                val resultPendingIntent = PendingIntent.getActivity(
                    applicationContext, 1, resultIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                mBuilder.setContentIntent(resultPendingIntent)
                startForeground(1, mBuilder.build())
            } else {
                @Suppress("DEPRECATION")
                startForeground(1, Notification())
            }
        }
        if (screenLockListener == null) {
            screenLockListener = ScreenLockListener(applicationContext)
            screenLockListener!!.registerListener()
        }
    }

    override fun onDestroy() {
        if (screenLockListener != null) {
            screenLockListener!!.unregisterListener()
            screenLockListener = null
        }
        @Suppress("DEPRECATION")
        stopForeground(true)
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }
}
