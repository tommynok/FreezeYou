package cf.playhi.freezeyou.utils

import android.Manifest
import android.app.*
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.sqlite.SQLiteDatabase
import android.net.ConnectivityManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.SystemClock
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.app.ActivityCompat
import cf.playhi.freezeyou.R
import cf.playhi.freezeyou.TriggerTasksService
import cf.playhi.freezeyou.receiver.TasksNeedExecuteReceiver
import cf.playhi.freezeyou.service.FUFService
import cf.playhi.freezeyou.service.ForceStopService
import cf.playhi.freezeyou.service.OneKeyFreezeService
import cf.playhi.freezeyou.service.OneKeyUFService
import cf.playhi.freezeyou.ui.ShowSimpleDialogActivity
import cf.playhi.freezeyou.utils.ProcessUtils.destroyProcess
import cf.playhi.freezeyou.utils.ServiceUtils.startService
import cf.playhi.freezeyou.utils.ToastUtils.showToast
import net.grandcentrix.tray.AppPreferences
import java.io.DataOutputStream
import java.util.*

object TasksUtils {
    @JvmStatic
    fun publishTask(
        context: Context, id: Int, hour: Int, minute: Int, repeat: String, task: String?
    ) {
        val alarmMgr = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager?
        val intent = Intent(context, TasksNeedExecuteReceiver::class.java)
            .putExtra("id", id)
            .putExtra("task", task)
            .putExtra("repeat", repeat)
            .putExtra("hour", hour)
            .putExtra("minute", minute)
        val alarmIntent = PendingIntent.getBroadcast(
            context, id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val calendar = Calendar.getInstance()
        val systemTime = System.currentTimeMillis()
        calendar.timeInMillis = systemTime
        calendar.set(Calendar.HOUR_OF_DAY, hour)
        calendar.set(Calendar.MINUTE, minute)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        if (alarmMgr != null) {
            if ("0" == repeat) {
                if (systemTime >= calendar.timeInMillis) {
                    calendar.add(Calendar.DAY_OF_MONTH, 1)
                }
                setTask(alarmMgr, calendar.timeInMillis, alarmIntent)
            } else {
                var timeInterval = Long.MAX_VALUE
                var timeTmp: Long
                for (i in 0 until repeat.length) {
                    when (repeat.substring(i, i + 1)) {
                        "1" -> calendar.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
                        "2" -> calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                        "3" -> calendar.set(Calendar.DAY_OF_WEEK, Calendar.TUESDAY)
                        "4" -> calendar.set(Calendar.DAY_OF_WEEK, Calendar.WEDNESDAY)
                        "5" -> calendar.set(Calendar.DAY_OF_WEEK, Calendar.THURSDAY)
                        "6" -> calendar.set(Calendar.DAY_OF_WEEK, Calendar.FRIDAY)
                        "7" -> calendar.set(Calendar.DAY_OF_WEEK, Calendar.SATURDAY)
                        else -> {}
                    }
                    timeTmp = calculateTimeInterval(systemTime, calendar.timeInMillis)
                    if (timeTmp <= 0) {
                        timeTmp += 604800000
                    }
                    if (timeTmp > 0 && timeTmp < timeInterval) {
                        timeInterval = timeTmp
                    }
                }
                setTask(alarmMgr, systemTime + timeInterval, alarmIntent)
            }
        } else {
            showToast(context, R.string.requestFailedPlsRetry)
        }
    }

    private fun setTask(
        alarmManager: AlarmManager,
        triggerAtMillis: Long,
        operation: PendingIntent
    ) { //RTC
        try {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, operation)
        } catch (e: SecurityException) {
            e.printStackTrace()
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, operation)
        }
    }

    private fun setRealTimeTask(
        alarmManager: AlarmManager,
        triggerAtMillis: Long,
        operation: PendingIntent
    ) {
        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggerAtMillis,
                operation
            )
        } catch (e: SecurityException) {
            e.printStackTrace()
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggerAtMillis,
                operation
            )
        }
    }

    private fun calculateTimeInterval(first: Long, last: Long): Long {
        return last - first
    }

    @JvmStatic
    fun runTask(task: String, context: Context, taskTrigger: String?) {
        val sTasks = task.split(";").toTypedArray()
        for (asTasks in sTasks) {
            val length = asTasks.length
            if (asTasks.lowercase(Locale.getDefault()).startsWith("okff")) {
                if (parseTaskAndReturnIfNeedExecuteImmediately(context, asTasks, taskTrigger)) startService(
                    context,
                    Intent(context, OneKeyFreezeService::class.java).putExtra(
                        "autoCheckAndLockScreen",
                        false
                    )
                )
            } else if (asTasks.lowercase(Locale.getDefault()).startsWith("okuf")) {
                if (parseTaskAndReturnIfNeedExecuteImmediately(context, asTasks, taskTrigger)) startService(
                    context,
                    Intent(context, OneKeyUFService::class.java)
                )
            } else if (length >= 2) {
                val string = asTasks.substring(0, 2).lowercase(Locale.getDefault())
                val tasks = if (length < 4) arrayOf() else asTasks.substring(3).split(",").toTypedArray()
                when (string) {
                    "ds" ->  //disableSettings
                        if (length >= 4 && parseTaskAndReturnIfNeedExecuteImmediately(
                                context,
                                asTasks,
                                taskTrigger
                            )
                        ) enableAndDisableSysSettings(tasks, context, false)
                    "es" ->  //enableSettings
                        if (length >= 4 && parseTaskAndReturnIfNeedExecuteImmediately(
                                context,
                                asTasks,
                                taskTrigger
                            )
                        ) enableAndDisableSysSettings(tasks, context, true)
                    "ff" -> if (length >= 4 && parseTaskAndReturnIfNeedExecuteImmediately(
                            context,
                            asTasks,
                            taskTrigger
                        )
                    ) startService(
                        context,
                        Intent(context, FUFService::class.java)
                            .putExtra(
                                "packages",
                                OneKeyListUtils.decodeUserListsInPackageNames(context, tasks)
                            )
                            .putExtra("freeze", true)
                    )
                    "lg" ->  //LOG.E
                        if (length >= 4 && parseTaskAndReturnIfNeedExecuteImmediately(
                                context,
                                asTasks,
                                taskTrigger
                            )
                        ) Log.e("TasksLogE", asTasks.substring(3))
                    "ls" ->  //Lock Screen
                        if (parseTaskAndReturnIfNeedExecuteImmediately(context, asTasks, taskTrigger)) {
                            if ("onScreenOn" != taskTrigger) {
                                DevicePolicyManagerUtils.doLockScreen(context)
                            }
                        }
                    "sn" ->  //show a notification
                        if (length >= 4 && parseTaskAndReturnIfNeedExecuteImmediately(
                                context,
                                asTasks,
                                taskTrigger
                            )
                        ) if (tasks.size == 2) showNotification(
                            context,
                            tasks[0],
                            tasks[1]
                        ) else showToast(context, R.string.invalidArguments)
                    "sp" ->  //getLaunchIntentForPackage,startActivity
                        if (length >= 4 && parseTaskAndReturnIfNeedExecuteImmediately(
                                context,
                                asTasks,
                                taskTrigger
                            )
                        ) startPackages(context, tasks)
                    "st" ->  //showToast
                        if (length >= 4 && parseTaskAndReturnIfNeedExecuteImmediately(
                                context,
                                asTasks,
                                taskTrigger
                            )
                        ) showToast(context, asTasks.substring(3))
                    "su" ->  //startActivity_uri
                        if (length >= 4 && parseTaskAndReturnIfNeedExecuteImmediately(
                                context,
                                asTasks,
                                taskTrigger
                            )
                        ) startActivityByUri(context, tasks)
                    "uf" -> if (length >= 4 && parseTaskAndReturnIfNeedExecuteImmediately(
                            context,
                            asTasks,
                            taskTrigger
                        )
                    ) startService(
                        context,
                        Intent(context, FUFService::class.java)
                            .putExtra(
                                "packages",
                                OneKeyListUtils.decodeUserListsInPackageNames(context, tasks)
                            )
                            .putExtra("freeze", false)
                    )
                    "fc" -> if (length >= 4 && parseTaskAndReturnIfNeedExecuteImmediately(
                            context,
                            asTasks,
                            taskTrigger
                        )
                    ) startService(
                        context,
                        Intent(context, ForceStopService::class.java)
                            .putExtra(
                                "packages",
                                OneKeyListUtils.decodeUserListsInPackageNames(context, tasks)
                            )
                    )
                    else -> {}
                }
            }
        }
    }

    private fun showNotification(context: Context, title: String, text: String) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager?
        if (notificationManager == null) {
            showToast(context, R.string.failed)
            return
        }
        val builder: Notification.Builder = if (Build.VERSION.SDK_INT < 26) {
            Notification.Builder(context)
        } else {
            val channel = NotificationChannel(
                "ScheduledTasksUserNotifications",
                context.getString(R.string.scheduledTasksUserNotification),
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
            Notification.Builder(context, "ScheduledTasksUserNotifications")
        }
        builder.setContentTitle(title)
        builder.setContentText(text)
        builder.setSmallIcon(R.drawable.ic_notification)
        val id = (Date().time.toString() + title + text).hashCode()
        builder.setContentIntent(
            PendingIntent.getActivity(
                context, id,
                Intent(context, ShowSimpleDialogActivity::class.java)
                    .putExtra("title", title)
                    .putExtra("text", text),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
        builder.setAutoCancel(true)
        notificationManager.notify(id, builder.build())
    }

    private fun startActivityByUri(context: Context, uris: Array<String>) {
        try {
            for (uriS in uris) {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uriS))
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            showToast(context, R.string.failed)
        }
    }

    private fun startPackages(context: Context, packages: Array<String>) {
        val pm = context.packageManager
        for (aPackage in packages) {
            val launchIntent = pm.getLaunchIntentForPackage(aPackage) ?: continue
            if (context is Activity) {
                context.startActivity(launchIntent)
            } else {
                context.startActivity(launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }

    private fun enableAndDisableSysSettings(tasks: Array<String>, context: Context, enable: Boolean) {
        for (aTask in tasks) {
            when (aTask) {
                "wifi" -> { // WiFi
                    val wifiManager =
                        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager?
                    wifiManager?.isWifiEnabled = enable
                }
                "cd" ->  // CellularData
                    setMobileDataEnabled(context, enable)
                "bluetooth" ->  // Bluetooth
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                        && ActivityCompat
                            .checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
                        != PackageManager.PERMISSION_GRANTED
                    ) {
                        showToast(context, R.string.bluetoothPermissionIsNotGranted)
                    } else {
                        if (enable) {
                            BluetoothAdapter.getDefaultAdapter().enable()
                        } else {
                            BluetoothAdapter.getDefaultAdapter().disable()
                        }
                    }
                else -> {}
            }
        }
    }

    private fun parseTaskAndReturnIfNeedExecuteImmediately(
        context: Context,
        task: String,
        taskTrigger: String?
    ): Boolean {
        val splitTask = task.split(" ").toTypedArray()
        val splitTaskLength = splitTask.size
        for (i in 0 until splitTaskLength) {
            when (splitTask[i]) {
                "-d" -> if (splitTaskLength >= i + 1) {
                    val delayAtSeconds = splitTask[i + 1].toLong()
                    val alarmMgr = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager?
                    val intent = Intent(context, TasksNeedExecuteReceiver::class.java)
                        .putExtra("id", -6)
                        .putExtra("task", task.replace(" -d " + splitTask[i + 1], ""))
                        .putExtra("repeat", "-1")
                        .putExtra("hour", -1)
                        .putExtra("minute", -1)
                    val requestCode = (task + Date()).hashCode()
                    val pendingIntent = PendingIntent.getBroadcast(
                        context,
                        requestCode,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT
                                or PendingIntent.FLAG_IMMUTABLE
                    )
                    createDelayTasks(alarmMgr, delayAtSeconds, pendingIntent)
                    if (taskTrigger != null) { //定时或无撤回判断能力或目前不计划实现撤销的任务直接null
                        val appPreferences = AppPreferences(context)
                        appPreferences.put(
                            taskTrigger,
                            appPreferences.getString(taskTrigger, "") + requestCode + ","
                        )
                    }
                    return false
                }
                else -> {}
            }
        }
        return true
    }

    private fun createDelayTasks(
        alarmManager: AlarmManager?,
        delayAtSeconds: Long,
        pendingIntent: PendingIntent
    ) {
        alarmManager?.let {
            setRealTimeTask(
                it,
                SystemClock.elapsedRealtime() + delayAtSeconds * 1000,
                pendingIntent
            )
        }
    }

    /**
     * As [runTask] contains [ToastUtils.showToast] related function,
     * this method should run on UI thread.
     */
    @JvmStatic
    fun onUFApplications(context: Context, pkgNameString: String) {
        DataStatisticsUtils.addUFreezeTimes(context, pkgNameString)
        val db = context.openOrCreateDatabase("scheduledTriggerTasks", Context.MODE_PRIVATE, null)
        db.execSQL(
            "create table if not exists tasks(_id integer primary key autoincrement,tg varchar,tgextra varchar,enabled integer(1),label varchar,task varchar,column1 varchar,column2 varchar)"
        )
        val cursor = db.query("tasks", null, null, null, null, null, null)
        if (cursor.moveToFirst()) {
            for (i in 0 until cursor.count) {
                var tgExtra = cursor.getString(cursor.getColumnIndexOrThrow("tgextra"))
                if (tgExtra == null) {
                    tgExtra = ""
                }
                val tg = cursor.getString(cursor.getColumnIndexOrThrow("tg"))
                val enabled = cursor.getInt(cursor.getColumnIndexOrThrow("enabled"))
                if (enabled == 1 && "onUFApplications" == tg && ("" == tgExtra || listOf(
                        *OneKeyListUtils.decodeUserListsInPackageNames(
                            context,
                            tgExtra.split(",").toTypedArray()
                        )
                    ).contains(pkgNameString))
                ) {
                    val task = cursor.getString(cursor.getColumnIndexOrThrow("task"))
                    if (task != null && "" != task) {
                        runTask(task.replace("[cpkgn]", pkgNameString), context, null)
                    }
                }
                cursor.moveToNext()
            }
        }
        cursor.close()
        db.close()
    }

    /**
     * As [runTask] contains [ToastUtils.showToast] related function,
     * this method should run on UI thread.
     */
    @JvmStatic
    fun onFApplications(context: Context, pkgNameString: String) {
        DataStatisticsUtils.addFreezeTimes(context, pkgNameString)
        val db = context.openOrCreateDatabase("scheduledTriggerTasks", Context.MODE_PRIVATE, null)
        db.execSQL(
            "create table if not exists tasks(_id integer primary key autoincrement,tg varchar,tgextra varchar,enabled integer(1),label varchar,task varchar,column1 varchar,column2 varchar)"
        )
        val cursor = db.query("tasks", null, null, null, null, null, null)
        if (cursor.moveToFirst()) {
            for (i in 0 until cursor.count) {
                val tg = cursor.getString(cursor.getColumnIndexOrThrow("tg"))
                var tgExtra = cursor.getString(cursor.getColumnIndexOrThrow("tgextra"))
                val enabled = cursor.getInt(cursor.getColumnIndexOrThrow("enabled"))
                if (tgExtra == null) {
                    tgExtra = ""
                }
                if (enabled == 1 && "onFApplications" == tg && ("" == tgExtra || listOf(
                        *OneKeyListUtils.decodeUserListsInPackageNames(
                            context,
                            tgExtra.split(",").toTypedArray()
                        )
                    ).contains(pkgNameString))
                ) {
                    val task = cursor.getString(cursor.getColumnIndexOrThrow("task"))
                    if (task != null && "" != task) {
                        runTask(task.replace("[cpkgn]", pkgNameString), context, null)
                    }
                }
                cursor.moveToNext()
            }
        }
        cursor.close()
        db.close()
    }

    private fun setMobileDataEnabled(context: Context, enable: Boolean) {
        //https://stackoverflow.com/questions/21511216/toggle-mobile-data-programmatically-on-android-4-4-2
        try { //4.4及以下
            val mConnectivityManager =
                context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val aClass: Class<*> = mConnectivityManager.javaClass
            val argsClass = arrayOfNulls<Class<*>>(1)
            argsClass[0] = Boolean::class.javaPrimitiveType
            val method = aClass.getMethod("setMobileDataEnabled", *argsClass)
            method.invoke(mConnectivityManager, enable)
        } catch (e: Exception) {
            e.printStackTrace()
            try { //pri-app方法
                val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
                val methodSet = Class.forName(tm.javaClass.name).getDeclaredMethod(
                    "setDataEnabled",
                    Boolean::class.javaPrimitiveType
                )
                methodSet.invoke(tm, true)
            } catch (ee: Exception) {
                ee.printStackTrace()
                try { //Root方法
                    val process = Runtime.getRuntime().exec("su")
                    val outputStream = DataOutputStream(process.outputStream)
                    outputStream.writeBytes("svc data " + (if (enable) "enable" else "disable") + "\n")
                    outputStream.writeBytes("exit\n")
                    outputStream.flush()
                    process.waitFor()
                    destroyProcess(outputStream, process)
                } catch (eee: Exception) { //暂时无计可施……
                    eee.printStackTrace()
                    showToast(context, R.string.failed)
                }
            }
        }
    }

    @JvmStatic
    fun cancelAllUnexecutedDelayTasks(context: Context, typeNeedsCheckTaskTrigger: String?) {
        if (typeNeedsCheckTaskTrigger != null) {
            val alarmMgr = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager?
            val intent = Intent(context, TasksNeedExecuteReceiver::class.java)
            val appPreferences = AppPreferences(context)
            var unprocessed = appPreferences.getString(typeNeedsCheckTaskTrigger, "")
            if (unprocessed == null) unprocessed = ""
            for (id in unprocessed.split(",").toTypedArray()) {
                if (id != "" && id != null) {
                    val alarmIntent = PendingIntent.getBroadcast(
                        context, id.toInt(), intent,
                        PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    alarmMgr?.cancel(alarmIntent)
                }
            }
            appPreferences.put(typeNeedsCheckTaskTrigger, "")
        }
    }

    @JvmStatic
    fun cancelTheTask(context: Context, id: Int) {
        val alarmMgr = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager?
        val intent = Intent(context, TasksNeedExecuteReceiver::class.java)
            .putExtra("id", id)
        val alarmIntent = PendingIntent.getBroadcast(
            context, id, intent,
            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmMgr?.cancel(alarmIntent)
    }

    @JvmStatic
    fun checkTimeTasks(context: Context) {
        val db = context.openOrCreateDatabase("scheduledTasks", Context.MODE_PRIVATE, null)
        db.execSQL(
            "create table if not exists tasks(_id integer primary key autoincrement,hour integer(2),minutes integer(2),repeat varchar,enabled integer(1),label varchar,task varchar,column1 varchar,column2 varchar)"
        )
        val cursor = db.query("tasks", null, null, null, null, null, null)
        if (cursor.moveToFirst()) {
            for (i in 0 until cursor.count) {
                val id = cursor.getInt(cursor.getColumnIndexOrThrow("_id"))
                val repeat = cursor.getString(cursor.getColumnIndexOrThrow("repeat"))
                val hour = cursor.getInt(cursor.getColumnIndexOrThrow("hour"))
                val minutes = cursor.getInt(cursor.getColumnIndexOrThrow("minutes"))
                val enabled = cursor.getInt(cursor.getColumnIndexOrThrow("enabled"))
                val task = cursor.getString(cursor.getColumnIndexOrThrow("task"))
                cancelTheTask(context, id)
                if (enabled == 1) {
                    publishTask(context, id, hour, minutes, repeat, task)
                }
                cursor.moveToNext()
            }
        }
        cursor.close()
        db.close()
    }

    @JvmStatic
    fun checkTriggerTasks(context: Context) {
        //事件触发器
        val db = context.openOrCreateDatabase("scheduledTriggerTasks", Context.MODE_PRIVATE, null)
        db.execSQL(
            "create table if not exists tasks(_id integer primary key autoincrement,tg varchar,tgextra varchar,enabled integer(1),label varchar,task varchar,column1 varchar,column2 varchar)"
        )
        val cursor = db.query("tasks", null, null, null, null, null, null)
        if (cursor.moveToFirst()) {
            for (i in 0 until cursor.count) {
                var tg = cursor.getString(cursor.getColumnIndexOrThrow("tg"))
                val enabled = cursor.getInt(cursor.getColumnIndexOrThrow("enabled"))
                if (enabled == 1) {
                    if (tg == null) {
                        tg = ""
                    }
                    when (tg) {
                        "onScreenOn" -> startService(
                            context,
                            Intent(context, TriggerTasksService::class.java)
                                .putExtra("OnScreenOn", true)
                        )
                        "onScreenOff" -> startService(
                            context,
                            Intent(context, TriggerTasksService::class.java)
                                .putExtra("OnScreenOff", true)
                        )
                        else -> {}
                    }
                }
                cursor.moveToNext()
            }
        }
        cursor.close()
        db.close()
    }
}
