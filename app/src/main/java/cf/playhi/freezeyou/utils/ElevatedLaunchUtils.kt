package cf.playhi.freezeyou.utils

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import cf.playhi.freezeyou.R
import cf.playhi.freezeyou.utils.DebugModeUtils.isDebugModeEnabled
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader

/**
 * Launching an activity another app declares as `exported="false"` throws a SecurityException for
 * an ordinary caller. A root shell, and the shell UID Shizuku runs as, are both allowed to start
 * arbitrary components — which is exactly what `adb shell am start` relies on. This is the retry
 * path used after the normal launch is refused, so a shortcut pointing at an internal activity
 * still works on a device that has either.
 */
object ElevatedLaunchUtils {

    /**
     * Runs off the main thread: both paths spawn a process and wait for it. The result is
     * reported back on the main thread, since Toast needs a Looper.
     */
    @JvmStatic
    fun startActivityElevatedAsync(context: Context, pkgName: String, target: String) {
        Thread {
            val launched = startActivityElevated(pkgName, target)
            Handler(Looper.getMainLooper()).post {
                ToastUtils.showToast(
                    context,
                    if (launched) R.string.executed else R.string.insufficientPermission
                )
            }
        }.start()
    }

    private fun startActivityElevated(pkgName: String, target: String): Boolean {
        val command = "am start -n $pkgName/$target"
        if (runViaRoot(command)) return true
        return runViaShizuku(command)
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
            succeeded(exitCode, output, "root")
        } catch (e: Exception) {
            e.printStackTrace()
            if (isDebugModeEnabled()) Log.e("DebugModeLogcat", "elevated launch via root failed: $e")
            false
        } finally {
            ProcessUtils.destroyProcess(outputStream, process)
        }
    }

    private fun runViaShizuku(command: String): Boolean {
        return try {
            if (!Shizuku.pingBinder()) return false
            // Reflection on purpose: newProcess is a restricted API and is not part of the
            // published surface, so a version without it degrades to "not available" instead of
            // failing to build.
            val method = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            method.isAccessible = true
            val process = method.invoke(
                null, arrayOf("sh", "-c", command), null, null
            ) as Process
            val output = readOutput(process)
            val exitCode = process.waitFor()
            succeeded(exitCode, output, "shizuku")
        } catch (e: Exception) {
            e.printStackTrace()
            if (isDebugModeEnabled()) Log.e("DebugModeLogcat", "elevated launch via shizuku failed: $e")
            false
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

    /**
     * `am start` exits 0 even when it could not start anything, printing the reason instead, so
     * the output has to be inspected as well as the exit code.
     */
    private fun succeeded(exitCode: Int, output: String, via: String): Boolean {
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
}
