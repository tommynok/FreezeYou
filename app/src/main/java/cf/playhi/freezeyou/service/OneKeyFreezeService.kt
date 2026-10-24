package cf.playhi.freezeyou.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import cf.playhi.freezeyou.R
import cf.playhi.freezeyou.app.FreezeYouBaseService
import cf.playhi.freezeyou.storage.key.DefaultMultiProcessMMKVStorageStringKeys.selectFUFMode
import cf.playhi.freezeyou.storage.key.DefaultMultiProcessMMKVStorageStringKeys.shortCutOneKeyFreezeAdditionalOptions
import cf.playhi.freezeyou.storage.mmkv.DefaultMultiProcessMMKVStorage
import cf.playhi.freezeyou.ui.AskLockScreenActivity
import cf.playhi.freezeyou.utils.DevicePolicyManagerUtils
import cf.playhi.freezeyou.utils.FUFUtils.oneKeyAction
import cf.playhi.freezeyou.utils.PackageListUtils

class OneKeyFreezeService : FreezeYouBaseService() {

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                "OneKeyFreeze",
                getString(R.string.oneKeyFreeze),
                NotificationManager.IMPORTANCE_NONE
            )
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager?
            notificationManager?.createNotificationChannel(channel)
            val mBuilder = Notification.Builder(this, "OneKeyFreeze")
            mBuilder.setSmallIcon(R.drawable.ic_notification)
            mBuilder.setContentText(getString(R.string.oneKeyFreeze))
            startForeground(2, mBuilder.build())
        } else {
            startForeground(2, Notification())
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            // Restart after the process was killed: nothing to act on.
            doFinish(startId)
            return super.onStartCommand(null, flags, startId)
        }
        val auto = intent.getBooleanExtra("autoCheckAndLockScreen", true)
        val pref = DefaultMultiProcessMMKVStorage()
        val pkgNames = PackageListUtils.parse(
            pref.getString(getString(R.string.sAutoFreezeApplicationList), "")
        )
        Thread {
            try {
                if (pkgNames.isNotEmpty()) {
                    oneKeyAction(
                        this, true,
                        pkgNames,
                        selectFUFMode.getValue()!!.toInt()
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                checkAuto(auto, this, startId)
            }
        }.start()
        return super.onStartCommand(intent, flags, startId)
    }

    private fun checkAndLockScreen(context: Context, startId: Int) {
        when (shortCutOneKeyFreezeAdditionalOptions.getValue(null)) {
            "askLockScreen" -> {
                startActivity(
                    Intent(
                        applicationContext,
                        AskLockScreenActivity::class.java
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                doFinish(startId)
            }
            "lockScreenImmediately" -> {
                DevicePolicyManagerUtils.doLockScreen(context)
                doFinish(startId)
            }
            "nothing" -> doFinish(startId)
            else -> doFinish(startId)
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
        mNotificationManager?.cancel(2)
        if (startId != -1) stopSelf(startId) else stopSelf()
    }

    private fun checkAuto(auto: Boolean, context: Context, startId: Int) {
        if (auto) {
            checkAndLockScreen(context, startId)
        } else {
            doFinish(startId)
        }
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }
}
