package cf.playhi.freezeyou.utils

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.os.Build
import java.io.DataOutputStream

object ProcessUtils {
    @JvmStatic
    fun destroyProcess(dataOutputStream: DataOutputStream?, process1: Process?) {
        try {
            dataOutputStream?.close()
            process1?.destroy()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @JvmStatic
    @Throws(Exception::class)
    fun fAURoot(pkgName: String, enable: Boolean, hideMode: Boolean): Int {
        val process = Runtime.getRuntime().exec("su")
        val outputStream = DataOutputStream(process.outputStream)
        if (enable) {
            outputStream.writeBytes("pm " + (if (hideMode) "unhide " else "enable ") + pkgName + "\n")
        } else {
            outputStream.writeBytes("pm " + (if (hideMode) "hide " else "disable ") + pkgName + "\n")
        }
        outputStream.writeBytes("exit\n")
        outputStream.flush()
        val i = process.waitFor()
        destroyProcess(outputStream, process)
        return i
    }

    /**
     * @param context Context
     * @return packageName:processName. If activityManager == null or pid not found, return ""
     */
    @JvmStatic
    fun getProcessName(context: Context): String {
        /*
         * References:
         * https://blog.csdn.net/zhe_ge_sha_shou/article/details/74333408
         * https://blog.csdn.net/weixin_35715335/article/details/117346298
         */
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Application.getProcessName()
        } else {
            val myPid = android.os.Process.myPid()
            var processName = ""
            val activityManager =
                context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager?
            if (activityManager != null) {
                val runningProcesses = activityManager.runningAppProcesses
                if (runningProcesses != null) {
                    for (info in runningProcesses) {
                        if (info.pid == myPid) {
                            processName = info.processName
                            break
                        }
                    }
                }
            }
            processName
        }
    }
}
