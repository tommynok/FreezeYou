package cf.playhi.freezeyou.utils

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import cf.playhi.freezeyou.R
import cf.playhi.freezeyou.utils.DebugModeUtils.isDebugModeEnabled
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStream
import java.io.InputStreamReader

/**
 * Launching an activity another app declares as `exported="false"` throws a SecurityException for
 * an ordinary caller. A root shell, and the shell uid Shizuku runs as, are both allowed to start
 * arbitrary components. This is the retry path used after the normal launch is refused, so a
 * shortcut pointing at an internal activity still works on a device that has either.
 */
object ElevatedLaunchUtils {

    /**
     * Runs off the main thread: both paths block. The result is reported back on the main thread,
     * since Toast needs a Looper.
     */
    @JvmStatic
    fun startActivityElevatedAsync(context: Context, pkgName: String, target: String) {
        // `su` blocks for as long as it takes, and the activity that asked may well be finished
        // by the time there is an answer — so the toast goes through the application context.
        val appContext = context.applicationContext
        Thread {
            val launched = startActivityElevated(pkgName, target)
            Handler(Looper.getMainLooper()).post {
                ToastUtils.showToast(
                    appContext,
                    if (launched) R.string.executed else R.string.insufficientPermission
                )
            }
        }.start()
    }

    private fun startActivityElevated(pkgName: String, target: String): Boolean {
        // Shizuku first: it is the mode this fork is normally used in, and unlike `su` it never
        // pops a permission prompt of its own when it is not set up.
        if (runViaShizuku(pkgName, target)) return true
        return runViaRoot(pkgName, target)
    }

    private fun runViaRoot(pkgName: String, target: String): Boolean {
        var process: Process? = null
        var outputStream: DataOutputStream? = null
        return try {
            process = Runtime.getRuntime().exec("su")
            outputStream = DataOutputStream(process.outputStream)
            outputStream.writeBytes("${amStartCommand(pkgName, target).joinToString(" ")}\n")
            outputStream.writeBytes("exit\n")
            outputStream.flush()
            val ok = succeeded(process, "root")
            ok
        } catch (e: Exception) {
            e.printStackTrace()
            if (isDebugModeEnabled()) Log.e("DebugModeLogcat", "elevated launch via root failed: $e")
            false
        } finally {
            ProcessUtils.destroyProcess(outputStream, process)
        }
    }

    /**
     * Runs `am start` inside Shizuku's own process rather than calling IActivityManager directly.
     *
     * The direct call cannot work: ActivityManagerService checks that the calling package belongs
     * to the calling uid, and over Shizuku the caller is shell or root, not this app — so passing
     * our own package name is refused with a SecurityException, which looked to the user like the
     * target activity being off limits. `am` already runs with that identity and states it
     * correctly, and it behaves the same whether Shizuku was started from adb or as root.
     */
    private fun runViaShizuku(pkgName: String, target: String): Boolean {
        var process: Process? = null
        try {
            if (Build.VERSION.SDK_INT < 23) return false
            if (!Shizuku.pingBinder()) return false
            // pingBinder only says the service is there; without the permission the call below
            // fails with a bare SecurityException that looks like the target refusing the launch.
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                if (isDebugModeEnabled()) {
                    Log.e("DebugModeLogcat", "elevated launch via shizuku: permission not granted")
                }
                return false
            }

            @Suppress("DEPRECATION")
            process = Shizuku.newProcess(amStartCommand(pkgName, target), null, null)
            return succeeded(process, "shizuku")
        } catch (e: Exception) {
            e.printStackTrace()
            if (isDebugModeEnabled()) {
                Log.e("DebugModeLogcat", "elevated launch via shizuku failed: $e")
            }
            return false
        } finally {
            if (process != null) ProcessUtils.destroyProcess(null, process)
        }
    }

    private fun amStartCommand(pkgName: String, target: String): Array<String> =
        arrayOf("am", "start", "-n", "$pkgName/$target")

    /**
     * `am start` exits 0 even when it started nothing, printing the reason instead — and it prints
     * that reason on stderr, so both streams have to be read.
     */
    private fun succeeded(process: Process, via: String): Boolean {
        val output = readStream(process.inputStream) + readStream(process.errorStream)
        val exitCode = process.waitFor()
        val ok = exitCode == 0 &&
                !output.contains("Error", ignoreCase = true) &&
                !output.contains("Exception", ignoreCase = true)
        if (isDebugModeEnabled()) {
            Log.e(
                "DebugModeLogcat",
                "elevated launch via $via: exit=$exitCode ok=$ok output=${output.trim()}"
            )
        }
        return ok
    }

    private fun readStream(stream: InputStream?): String {
        if (stream == null) return ""
        return try {
            val builder = StringBuilder()
            BufferedReader(InputStreamReader(stream)).use { reader ->
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
