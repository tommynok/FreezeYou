package cf.playhi.freezeyou.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import android.os.IBinder
import cf.playhi.freezeyou.R
import cf.playhi.freezeyou.app.FreezeYouBaseService
import cf.playhi.freezeyou.storage.key.DefaultMultiProcessMMKVStorageStringKeys.selectFUFMode
import cf.playhi.freezeyou.storage.mmkv.DefaultMultiProcessMMKVStorage
import cf.playhi.freezeyou.utils.FUFUtils.oneKeyAction
import cf.playhi.freezeyou.utils.PackageListUtils
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class OneKeyUFService : FreezeYouBaseService() {

    private val mExecutor = Executors.newSingleThreadExecutor()
    private val mActiveTasks = AtomicInteger(0)

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                "OneKeyUF", getString(R.string.oneKeyUF), NotificationManager.IMPORTANCE_NONE
            )
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager?
            notificationManager?.createNotificationChannel(channel)
            val mBuilder = Notification.Builder(this, "OneKeyUF")
            mBuilder.setSmallIcon(R.drawable.ic_notification)
            mBuilder.setContentText(getString(R.string.oneKeyUF))
            startForeground(3, mBuilder.build())
        } else {
            startForeground(3, Notification())
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            // Restart after the process was killed: nothing to act on.
            if (mActiveTasks.get() == 0) {
                doFinish(startId)
            } else {
                stopSelf(startId)
            }
            return super.onStartCommand(null, flags, startId)
        }
        val pref = DefaultMultiProcessMMKVStorage()
        val pkgNames = PackageListUtils.parse(
            pref.getString(getString(R.string.sOneKeyUFApplicationList), "")
        )
        mActiveTasks.incrementAndGet()
        mExecutor.execute {
            try {
                if (pkgNames.isNotEmpty()) {
                    oneKeyAction(
                        this, false,
                        pkgNames,
                        selectFUFMode.getValue()!!.toInt()
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                finishTask(startId)
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun finishTask(startId: Int) {
        if (mActiveTasks.decrementAndGet() == 0) {
            doFinish(startId)
        } else {
            stopSelf(startId)
        }
    }

    private fun doFinish(startId: Int = -1) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        val mNotificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager?
        mNotificationManager?.cancel(3)
        if (startId != -1) stopSelf(startId) else stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        mExecutor.shutdown()
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }
}
