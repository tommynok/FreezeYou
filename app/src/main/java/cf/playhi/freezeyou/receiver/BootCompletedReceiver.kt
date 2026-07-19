package cf.playhi.freezeyou.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Environment
import androidx.preference.PreferenceManager
import cf.playhi.freezeyou.service.ScreenLockOneKeyFreezeService
import cf.playhi.freezeyou.storage.key.DefaultMultiProcessMMKVStorageBooleanKeys.onekeyFreezeWhenLockScreen
import cf.playhi.freezeyou.utils.*
import cf.playhi.freezeyou.utils.FUFUtils.checkAndCreateFUFQuickNotification
import cf.playhi.freezeyou.utils.FUFUtils.checkMRootFrozen
import cf.playhi.freezeyou.utils.FUFUtils.checkRootFrozen
import net.grandcentrix.tray.AppPreferences
import java.io.File
import java.io.IOException

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == null) {
            return
        }
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                runBackgroundService(context)
                checkAndReNotifyNotifications(context)
                checkTasks(context)
            }
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                runBackgroundService(context)
                checkAndReNotifyNotifications(context)
                checkTasks(context)
                cleanExternalCache(context)
                val sharedPreferences = context.getSharedPreferences("Ver", Context.MODE_PRIVATE)
                if (sharedPreferences.getInt("Ver", 0) < VersionUtils.getVersionCode(context)) {
                    clearCrashLogs()
                }
            }
            else -> {}
        }
    }

    private fun cleanExternalCache(context: Context) {
        try {
            FileUtils.deleteAllFiles(context.externalCacheDir, false)
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun runBackgroundService(context: Context) {
        if (onekeyFreezeWhenLockScreen.getValue(null)) {
            ServiceUtils.startService(
                context,
                Intent(context, ScreenLockOneKeyFreezeService::class.java)
            )
        }
    }

    private fun checkAndReNotifyNotifications(context: Context) {
        val defaultSharedPreferences = AppPreferences(context)
        val string = defaultSharedPreferences.getString("notifying", "")
        if (!string.isNullOrEmpty()) {
            val strings = string.split(",").toTypedArray()
            val pm = context.packageManager
            for (aPkgName in strings) {
                if (aPkgName.isNotEmpty() && !checkFrozenStatus(context, aPkgName, pm)) {
                    checkAndCreateFUFQuickNotification(context, aPkgName)
                }
            }
        }
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        val oldNotifying = sharedPreferences.getString("notifying", "")
        if (!oldNotifying.isNullOrEmpty()) {
            val oldNotifyings = oldNotifying.split(",").toTypedArray()
            val pm = context.packageManager
            for (aPkgName in oldNotifyings) {
                if (aPkgName.isNotEmpty() && !checkFrozenStatus(context, aPkgName, pm)) {
                    checkAndCreateFUFQuickNotification(context, aPkgName)
                }
            }
            sharedPreferences.edit().putString("notifying", "").apply()
        }
    }

    private fun checkFrozenStatus(context: Context, packageName: String, pm: PackageManager): Boolean {
        return checkRootFrozen(context, packageName, pm) || checkMRootFrozen(context, packageName)
    }

    private fun checkTasks(context: Context) {
        checkTimeTasks(context)
        checkTriggerTasks(context)
    }

    private fun checkTimeTasks(context: Context) {
        TasksUtils.checkTimeTasks(context)
    }

    private fun checkTriggerTasks(context: Context) {
        TasksUtils.checkTriggerTasks(context)
    }

    private fun clearCrashLogs() {
        if (Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED) {
            val logPath = (Environment.getExternalStorageDirectory()
                .absolutePath + File.separator + File.separator
                    + "FreezeYou"
                    + File.separator
                    + "Log")
            try {
                FileUtils.deleteAllFiles(File(logPath), false)
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
        val logPath2 = (Environment.getDataDirectory().path
                + File.separator
                + "data"
                + File.separator
                + "cf.playhi.freezeyou"
                + File.separator
                + "log")
        try {
            FileUtils.deleteAllFiles(File(logPath2), false)
        } catch (e: IOException) {
            e.printStackTrace()
        }
        val crashCheck = File(
            logPath2
                    + File.separator
                    + "NeedUpload.log"
        )
        if (crashCheck.exists()) {
            crashCheck.delete()
        }
    }
}
