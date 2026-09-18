package cf.playhi.freezeyou.utils

import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import cf.playhi.freezeyou.R
import cf.playhi.freezeyou.utils.DebugModeUtils.isDebugModeEnabled
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader

/**
 * Launching an activity another app declares as `exported="false"` throws a SecurityException for
 * an ordinary caller. A root shell, and the shell UID Shizuku runs as, are both allowed to start
 * arbitrary components. This is the retry path used after the normal launch is refused, so a
 * shortcut pointing at an internal activity still works on a device that has either.
 *
 * The Shizuku path goes through IActivityManager rather than spawning `am`, which is how Shizuku
 * is meant to be used (and how RunningAppsUtils already talks to it here): it returns a real
 * result code instead of text that has to be guessed at.
 */
object ElevatedLaunchUtils {

    /**
     * Runs off the main thread: both paths block. The result is reported back on the main thread,
     * since Toast needs a Looper.
     */
    @JvmStatic
    fun startActivityElevatedAsync(context: Context, pkgName: String, target: String) {
        Thread {
            val launched = startActivityElevated(context, pkgName, target)
            Handler(Looper.getMainLooper()).post {
                ToastUtils.showToast(
                    context,
                    if (launched) R.string.executed else R.string.insufficientPermission
                )
            }
        }.start()
    }

    private fun startActivityElevated(context: Context, pkgName: String, target: String): Boolean {
        if (runViaRoot("am start -n $pkgName/$target")) return true
        return runViaShizuku(context, pkgName, target)
    }

    private fun runViaRoot(command: String): Boolean {
        var process: Process? = null
        var outputStream: DataOutputStream? = null
        return try {
            process = Runtime.getRuntime().exec("su")
            outputStream = DataOutputStream(process.outputStream)
            outputStream.writeBytes("$command\n")
            outputStream.writeBytes("exit\n")
            outputStream.flush()
            val output = readOutput(process)
            val exitCode = process.waitFor()
            // `am start` exits 0 even when it started nothing, printing the reason instead, so
            // the output has to be inspected as well as the exit code.
            val ok = exitCode == 0 &&
                    !output.contains("Error", ignoreCase = true) &&
                    !output.contains("Exception", ignoreCase = true)
            if (isDebugModeEnabled()) {
                Log.e(
                    "DebugModeLogcat",
                    "elevated launch via root: exit=$exitCode ok=$ok output=${output.trim()}"
                )
            }
            ok
        } catch (e: Exception) {
            e.printStackTrace()
            if (isDebugModeEnabled()) Log.e("DebugModeLogcat", "elevated launch via root failed: $e")
            false
        } finally {
            ProcessUtils.destroyProcess(outputStream, process)
        }
    }

    private fun runViaShizuku(context: Context, pkgName: String, target: String): Boolean {
        try {
            if (Build.VERSION.SDK_INT < 23) return false
            if (!Shizuku.pingBinder()) return false
            // pingBinder only says the service is there. Without this the binder call below fails
            // with a bare SecurityException that looks like the target refusing the launch.
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                if (isDebugModeEnabled()) {
                    Log.e("DebugModeLogcat", "elevated launch via shizuku: permission not granted")
                }
                return false
            }

            val intent = Intent()
                .setComponent(ComponentName(pkgName, target))
                .setAction(Intent.ACTION_MAIN)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            // Reflection because there is no hidden-API stub dependency in this project; the same
            // approach is already used for IActivityManager in RunningAppsUtils.
            val iActivityManager = Class.forName("android.app.IActivityManager")
            val activityManager = Class.forName("android.app.IActivityManager\$Stub")
                .getMethod("asInterface", IBinder::class.java)
                .invoke(
                    null,
                    ShizukuBinderWrapper(SystemServiceHelper.getSystemService(Context.ACTIVITY_SERVICE))
                ) ?: return false

            val applicationThread = Class.forName("android.app.IApplicationThread")
            val profilerInfo = Class.forName("android.app.ProfilerInfo")
            val callingPackage = context.packageName

            val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                iActivityManager.getMethod(
                    "startActivityWithFeature",
                    applicationThread, String::class.java, String::class.java, Intent::class.java,
                    String::class.java, IBinder::class.java, String::class.java,
                    Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                    profilerInfo, Bundle::class.java
                ).invoke(
                    activityManager, null, callingPackage, null, intent,
                    null, null, null, 0, 0, null, null
                ) as Int
            } else {
                iActivityManager.getMethod(
                    "startActivity",
                    applicationThread, String::class.java, Intent::class.java,
                    String::class.java, IBinder::class.java, String::class.java,
                    Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                    profilerInfo, Bundle::class.java
                ).invoke(
                    activityManager, null, callingPackage, intent,
                    null, null, null, 0, 0, null, null
                ) as Int
            }

            val ok = result == ActivityManager.START_SUCCESS
            if (isDebugModeEnabled()) {
                Log.e("DebugModeLogcat", "elevated launch via shizuku: result=$result ok=$ok")
            }
            return ok
        } catch (e: Exception) {
            e.printStackTrace()
            if (isDebugModeEnabled()) {
                Log.e("DebugModeLogcat", "elevated launch via shizuku failed: $e")
            }
            return false
        }
    }

    private fun readOutput(process: Process): String {
        return try {
            val builder = StringBuilder()
            BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                var line = reader.readLine()
                while (line != null) {
                    builder.append(line).append('\n')
                    line = reader.readLine()
                }
            }
            builder.toString()
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }
}
