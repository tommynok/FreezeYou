package cf.playhi.freezeyou

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.util.*

//部分参考 https://blog.csdn.net/soul_code/article/details/50601960
internal class CrashHandler : Thread.UncaughtExceptionHandler {
    private lateinit var mContext: Context
    fun init(context: Context) {
        Thread.setDefaultUncaughtExceptionHandler(this)
        mContext = context
    }

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        val date = Date()
        val logPath = mContext.cacheDir.toString() + File.separator + "log"
        saveLog(throwable, logPath, date)
        val extCacheDir = mContext.externalCacheDir
        if (extCacheDir != null) {
            saveLog(throwable, extCacheDir.toString() + File.separator + File.separator + "Log", date)
        }
        saveLocationAndMore(logPath, date)
        throwable.printStackTrace()
        android.os.Process.killProcess(android.os.Process.myPid())
    }

    private fun saveLog(throwable: Throwable, logPath: String, date: Date) {
        val file = File(logPath)
        if (!file.exists()) {
            if (!file.mkdirs()) {
                Log.e("crash handler", "mkdirs failed")
            }
        }
        try {
            val fw = FileWriter(
                logPath + File.separator
                        + date.time + ".log", true
            )
            fw.write(date.toString() + "\n")
            fw.write(
                "Model: " + Build.MODEL + ","
                        + Build.VERSION.SDK_INT + ","
                        + Build.VERSION.RELEASE + "\n"
            )
            val stackTrace = throwable.stackTrace
            val packageInfo = mContext.packageManager.getPackageInfo("cf.playhi.freezeyou", 0)
            fw.write("VN:" + packageInfo.versionName + "\n")
            @Suppress("DEPRECATION")
            fw.write("VC:" + packageInfo.versionCode + "\n")
            fw.write("LM:" + throwable.localizedMessage + "\n")
            fw.write("RM:" + throwable.message + "\n")
            for (aStackTrace in stackTrace) {
                fw.write(
                    "File:" + aStackTrace.fileName + " Class:"
                            + aStackTrace.className + " Method:"
                            + aStackTrace.methodName + " Line:"
                            + aStackTrace.lineNumber + "\n"
                )
            }
            fw.write("\n")
            fw.close()
        } catch (e: Exception) {
            Log.e("crash handler", "load file failed...", e.cause)
        }
    }

    private fun saveLocationAndMore(filePath: String, date: Date) {
        //保存需要提交文件位置
        val file = File(filePath)
        if (!file.exists()) {
            if (!file.mkdirs()) {
                Log.e("crash handler", "mkdirs failed")
            }
        }
        try {
            val fw = FileWriter(
                filePath
                        + File.separator
                        + "NeedUpload.log", true
            )
            fw.write(
                filePath + File.separator
                        + date.time + ".log" + "\n"
            )
            fw.write("\n")
            fw.close()
        } catch (e: Exception) {
            Log.e("crash handler", "load file failed...", e.cause)
        }
    }
}
