package cf.playhi.freezeyou

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import cf.playhi.freezeyou.app.FreezeYouBaseService
import cf.playhi.freezeyou.storage.key.DefaultMultiProcessMMKVStorageBooleanKeys.useForegroundService
import cf.playhi.freezeyou.utils.TasksUtils
import cf.playhi.freezeyou.utils.TasksUtils.cancelAllUnexecutedDelayTasks

class TriggerTasksService : FreezeYouBaseService() {
    private var triggerScreenLockListener: TriggerScreenLockListener? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            if (triggerScreenLockListener == null && intent.getBooleanExtra("OnScreenOn", false)) {
                triggerScreenLockListener = TriggerScreenLockListener(applicationContext)
                triggerScreenLockListener!!.registerListener()
            }
            if (triggerScreenLockListener == null && intent.getBooleanExtra("OnScreenOff", false)) {
                triggerScreenLockListener = TriggerScreenLockListener(applicationContext)
                triggerScreenLockListener!!.registerListener()
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

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
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onDestroy() {
        if (triggerScreenLockListener != null) {
            triggerScreenLockListener!!.unregisterListener()
            triggerScreenLockListener = null
        }
        @Suppress("DEPRECATION")
        stopForeground(true)
        super.onDestroy()
    }
}

internal class TriggerScreenLockListener(private val mContext: Context) {
    private val mScreenLockReceiver: ScreenLockBroadcastReceiver = ScreenLockBroadcastReceiver()

    private class ScreenLockBroadcastReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            val db = context.openOrCreateDatabase("scheduledTriggerTasks", Context.MODE_PRIVATE, null)
            db.execSQL(
                "create table if not exists tasks(_id integer primary key autoincrement,tg varchar,tgextra varchar,enabled integer(1),label varchar,task varchar,column1 varchar,column2 varchar)"
            )
            val cursor = db.query("tasks", null, null, null, null, null, null)
            if (action != null && cursor.moveToFirst()) {
                when (action) {
                    Intent.ACTION_SCREEN_OFF -> onActionScreenOnOff(context, cursor, false)
                    Intent.ACTION_SCREEN_ON -> onActionScreenOnOff(context, cursor, true)
                    else -> {}
                }
            }
            cursor.close()
            db.close()
        }

        companion object {
            private fun onActionScreenOnOff(
                context: Context,
                cursor: android.database.Cursor,
                screenOn: Boolean
            ) {
                cancelAllUnexecutedDelayTasks(context, if (screenOn) "onScreenOff" else "onScreenOn")
                for (i in 0 until cursor.count) {
                    val tg = cursor.getString(cursor.getColumnIndexOrThrow("tg"))
                    val enabled = cursor.getInt(cursor.getColumnIndexOrThrow("enabled"))
                    if (enabled == 1 && (if (screenOn) "onScreenOn" else "onScreenOff") == tg) {
                        val task = cursor.getString(cursor.getColumnIndexOrThrow("task"))
                        if (task != null && "" != task) {
                            TasksUtils.runTask(
                                task,
                                context,
                                if (screenOn) "onScreenOn" else "onScreenOff"
                            )
                        }
                    }
                    cursor.moveToNext()
                }
            }
        }
    }

    fun registerListener() {
        val filter = IntentFilter()
        filter.addAction(Intent.ACTION_SCREEN_OFF)
        filter.addAction(Intent.ACTION_SCREEN_ON)
        ContextCompat.registerReceiver(
            mContext,
            mScreenLockReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    fun unregisterListener() {
        mContext.unregisterReceiver(mScreenLockReceiver)
    }
}
