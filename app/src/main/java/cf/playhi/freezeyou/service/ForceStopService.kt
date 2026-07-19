package cf.playhi.freezeyou.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import android.os.IBinder
import cf.playhi.freezeyou.R
import cf.playhi.freezeyou.app.FreezeYouBaseService
import cf.playhi.freezeyou.utils.ForceStopUtils

class ForceStopService : FreezeYouBaseService() {
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return super.onStartCommand(null, flags, startId)
        val context = applicationContext
        val packages = intent.getStringArrayExtra("packages")
        ForceStopUtils.forceStop(context, packages)
        stopSelf()
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= 26) {
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(
                NotificationChannel(
                    "ForceStop", getString(R.string.forceStop), NotificationManager.IMPORTANCE_NONE
                )
            )
            val mBuilder = Notification.Builder(this, "ForceStop")
            mBuilder.setSmallIcon(R.drawable.ic_notification)
            mBuilder.setContentText(getString(R.string.forceStop))
            startForeground(6, mBuilder.build())
        } else {
            @Suppress("DEPRECATION")
            startForeground(6, Notification())
        }
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }
}
