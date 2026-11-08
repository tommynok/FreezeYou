package cf.playhi.freezeyou.utils

import android.content.Context
import android.os.Build
import android.text.format.Formatter
import android.util.Log
import cf.playhi.freezeyou.R
import cf.playhi.freezeyou.storage.key.DefaultMultiProcessMMKVStorageStringKeys.selectFUFMode
import cf.playhi.freezeyou.utils.DebugModeUtils.isDebugModeEnabled
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuProvider

/**
 * Android hides other apps' running processes from unprivileged callers since API 21, so a
 * "running apps" filter can only work in the modes that already carry elevated access (ROOT or
 * Shizuku) — same modes FUFSinglePackage already uses to freeze/unfreeze without root's usual
 * per-app sandbox restrictions.
 */
object RunningAppsUtils {

    private const val API_FREEZEYOU_ROOT_DISABLE_ENABLE = 2
    private const val API_FREEZEYOU_ROOT_UNHIDE_HIDE = 3
    private val ROOT_API_MODES = setOf(API_FREEZEYOU_ROOT_DISABLE_ENABLE, API_FREEZEYOU_ROOT_UNHIDE_HIDE)

    private const val API_FREEZEYOU_SHIZUKU_SYSTEM_APP_ENABLE_DISABLE_UNTIL_USED = 8
    private const val API_FREEZEYOU_SHIZUKU_SYSTEM_APP_ENABLE_DISABLE_USER = 9
    private const val API_FREEZEYOU_SHIZUKU_SYSTEM_APP_ENABLE_DISABLE = 10
    private val SHIZUKU_API_MODES = setOf(
        API_FREEZEYOU_SHIZUKU_SYSTEM_APP_ENABLE_DISABLE_UNTIL_USED,
        API_FREEZEYOU_SHIZUKU_SYSTEM_APP_ENABLE_DISABLE_USER,
        API_FREEZEYOU_SHIZUKU_SYSTEM_APP_ENABLE_DISABLE
    )

    @JvmStatic
    fun isRunningFilterAvailable(): Boolean {
        val apiMode = selectFUFMode.getValue()?.toIntOrNull() ?: return false
        return apiMode in ROOT_API_MODES || apiMode in SHIZUKU_API_MODES
    }

    /**
     * The freeze mode only decides which source to *try first*. Both sources are independent of
     * it — a device can have root granted while running in a Shizuku freeze mode, and either
     * path can come back empty (no su binary, Shizuku not granted). Falling back to the other
     * source turns that into a populated list instead of a silently empty one.
     */
    @JvmStatic
    fun getRunningPackages(context: Context): Set<String> {
        val apiMode = selectFUFMode.getValue()?.toIntOrNull()
        val shizukuFirst = apiMode in SHIZUKU_API_MODES

        val primary =
            if (shizukuFirst) getShizukuRunningPackages(context)
            else ProcessUtils.getRootRunningPackages()
        if (primary.isNotEmpty()) return primary

        val fallback =
            if (shizukuFirst) ProcessUtils.getRootRunningPackages()
            else getShizukuRunningPackages(context)
        if (isDebugModeEnabled()) {
            Log.e(
                "DebugModeLogcat",
                "getRunningPackages: primary(${if (shizukuFirst) "shizuku" else "root"}) empty, " +
                        "fallback returned ${fallback.size}"
            )
        }
        return fallback
    }

    data class ResourceUsage(val cpuPercent: Double?, val pssBytes: Long?)

    private const val RESOURCE_TIMEOUT_MS = 8_000L
    // Mirrors the root /proc scan budget: a Shizuku shell that stops answering must not wedge the
    // list worker forever, so the read goes through the deadline overload that can abandon it.
    private const val SHIZUKU_RUNNING_TIMEOUT_MS = 10_000L
    private const val CPU_MARKER = "__FREEZEYOU_CPU__"
    private const val STATM_MARKER = "__FREEZEYOU_STATM__"
    private const val PAGE_SIZE_BYTES = 4096L
    // No "dumpsys -t": a self-timeout truncates meminfo under load and drops apps' RAM rows.
    // statm fallback for service-only PIDs: one grep over /proc, not a per-PID loop that outran the 8s deadline.
    internal const val RESOURCE_COMMAND =
        "dumpsys meminfo 2>/dev/null | sed -n '/Total PSS by process:/,/^$/p' & " +
            "memory_pid=\$!; " +
            "cpu_output=\$(top -b -n 2 -d 1 -o PID,%CPU,ARGS 2>/dev/null); " +
            "wait \"\$memory_pid\"; printf '\\n__FREEZEYOU_CPU__\\n%s\\n' \"\$cpu_output\"; " +
            "printf '\\n__FREEZEYOU_STATM__\\n'; " +
            "grep -H '^' /proc/[0-9]*/statm 2>/dev/null || true"
    private val memoryRow = Regex("^\\s*([\\d,]+)K:\\s+(\\S+)\\s+\\(pid\\s+(\\d+)(?:\\s|\\)).*")
    private val cpuRow = Regex("^\\s*(\\d+)\\s+([\\d.]+)\\s+(\\S+)(?:\\s.*)?$")
    private val cpuHeader = Regex("^\\s*PID\\s+%CPU\\s+ARGS\\s*$")
    private val statmRow = Regex("^/proc/(\\d+)/statm:\\s*\\d+\\s+(\\d+)(?:\\s.*)?$")

    @JvmStatic
    fun getResourceUsage(context: Context, packageNames: Set<String>): Map<String, ResourceUsage> {
        if (packageNames.isEmpty()) return emptyMap()
        val shizukuFirst = selectFUFMode.getValue()?.toIntOrNull() in SHIZUKU_API_MODES
        for (useShizuku in listOf(shizukuFirst, !shizukuFirst)) {
            if (Thread.currentThread().isInterrupted) return emptyMap()
            try {
                if (useShizuku && !PrivilegedShellUtils.isShizukuAvailable(context)) continue
                val result = if (useShizuku) {
                    PrivilegedShellUtils.runShizukuCommand(RESOURCE_COMMAND, RESOURCE_TIMEOUT_MS)
                } else {
                    PrivilegedShellUtils.runRootCommand(RESOURCE_COMMAND, RESOURCE_TIMEOUT_MS)
                }
                if (result.exitCode != 0) continue
                val usage = parseResourceUsage(result.output, packageNames)
                if (usage.isNotEmpty()) return usage
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                return emptyMap()
            } catch (e: Exception) {
                if (isDebugModeEnabled()) Log.e("DebugModeLogcat", "getResourceUsage failed", e)
            }
        }
        return emptyMap()
    }

    @JvmStatic
    fun parseResourceUsage(output: String, packageNames: Set<String>): Map<String, ResourceUsage> {
        val sections = output.split(CPU_MARKER, limit = 2)
        val memory = linkedMapOf<Int, Pair<String, Long>>()
        for (line in sections[0].lineSequence()) {
            val match = memoryRow.matchEntire(line) ?: continue
            val kb = match.groupValues[1].replace(",", "").toLongOrNull() ?: continue
            if (kb > Long.MAX_VALUE / 1024) continue
            val pid = match.groupValues[3].toIntOrNull()?.takeIf { it > 0 } ?: continue
            memory[pid] = match.groupValues[2] to kb * 1024
        }
        val cpuAndStatm = if (sections.size == 2) sections[1].split(STATM_MARKER, limit = 2) else emptyList()
        val cpu = linkedMapOf<Int, Pair<String, Double>>()
        var samples = 0
        if (cpuAndStatm.isNotEmpty()) {
            for (line in cpuAndStatm[0].lineSequence()) {
                if (cpuHeader.matches(line.replace('[', ' ').replace(']', ' '))) {
                    samples++
                    cpu.clear()
                    continue
                }
                // Only the second top sample measures the requested interval.
                if (samples < 2) continue
                val match = cpuRow.matchEntire(line) ?: continue
                val pid = match.groupValues[1].toIntOrNull()?.takeIf { it > 0 } ?: continue
                val percent = match.groupValues[2].toDoubleOrNull()?.takeIf { it.isFinite() } ?: continue
                cpu[pid] = match.groupValues[3] to percent
            }
        }
        val statm = linkedMapOf<Int, Long>()
        if (cpuAndStatm.size == 2) {
            for (line in cpuAndStatm[1].lineSequence()) {
                val match = statmRow.matchEntire(line) ?: continue
                val pid = match.groupValues[1].toIntOrNull()?.takeIf { it > 0 } ?: continue
                val pages = match.groupValues[2].toLongOrNull() ?: continue
                if (pages > Long.MAX_VALUE / PAGE_SIZE_BYTES) continue
                statm[pid] = pages * PAGE_SIZE_BYTES
            }
        }
        val usage = linkedMapOf<String, ResourceUsage>()
        for (pid in memory.keys + cpu.keys) {
            val mem = memory[pid]
            val load = cpu[pid]
            if (mem != null && load != null && mem.first != load.first) continue
            val packageName = (mem?.first ?: load?.first ?: continue).substringBefore(':')
            if (packageName !in packageNames) continue
            val memBytes = mem?.second ?: statm[pid]
            val previous = usage[packageName]
            usage[packageName] = if (previous == null) {
                ResourceUsage(load?.second, memBytes)
            } else {
                ResourceUsage(
                    when {
                        previous.cpuPercent != null && load != null -> previous.cpuPercent + load.second
                        previous.cpuPercent != null -> previous.cpuPercent
                        load != null -> load.second
                        else -> null
                    },
                    when {
                        previous.pssBytes != null && memBytes != null && previous.pssBytes <= Long.MAX_VALUE - memBytes ->
                            previous.pssBytes + memBytes
                        previous.pssBytes != null -> previous.pssBytes
                        memBytes != null -> memBytes
                        else -> null
                    }
                )
            }
        }
        return usage
    }

    @JvmStatic
    fun formatResourceUsage(context: Context, usage: ResourceUsage?): String {
        val cpu = usage?.cpuPercent?.let {
            String.format(context.resources.configuration.locale, "%.1f%%", it)
        } ?: "—"
        val memory = usage?.pssBytes?.let { Formatter.formatShortFileSize(context, it) } ?: "—"
        return context.getString(R.string.runningAppStats, cpu, memory)
    }

    private fun getShizukuRunningPackages(context: Context): Set<String> {
        val debug = isDebugModeEnabled()
        var process: java.lang.Process? = null
        try {
            if (Build.VERSION.SDK_INT < 23) return emptySet()

            if (!Shizuku.pingBinder()) {
                ShizukuProvider.requestBinderForNonProviderProcess(context)
                var waited = 0
                while (!Shizuku.pingBinder() && waited < 3000) {
                    Thread.sleep(50)
                    waited += 50
                }
                if (!Shizuku.pingBinder()) {
                    if (debug) Log.e("DebugModeLogcat", "getShizukuRunningPackages: binder never came alive")
                    return emptySet()
                }
            }

            // The binder route (IActivityManager$Stub.asInterface via reflection) is a dead end:
            // that hidden API is max-target-R, blocked outright for targetSdk 31+, so
            // Class.getMethod throws before any binder call — IPackageManager's asInterface only
            // survives because it stayed on the warn-but-allow list. The shell route runs the
            // same /proc scan as the root path, as the user Shizuku itself runs under (root via
            // Sui, shell via adb): no hidden API in the chain.
            // Commands are joined with ";" and not "&&": the /proc scan loop can exit non-zero
            // (an unreadable cmdline fails the last "read"), and "&&" would then skip the
            // markers and ps fallbacks entirely — the same invariant the su path gets for free
            // by writing each command on its own line.
            process = Shizuku.newProcess(
                arrayOf("sh", "-c", ProcessUtils.RUNNING_PACKAGES_COMMANDS.joinToString("; ")),
                null, null
            )
            val packages = ProcessUtils.parseRunningPackages(process.inputStream, SHIZUKU_RUNNING_TIMEOUT_MS)
            process.waitFor()
            if (debug) {
                Log.e("DebugModeLogcat", "getShizukuRunningPackages: packages=${packages.size}")
            }
            return packages
        } catch (e: Exception) {
            e.printStackTrace()
            if (debug) Log.e("DebugModeLogcat", "getShizukuRunningPackages failed: $e")
            return emptySet()
        } finally {
            process?.destroy()
        }
    }
}
