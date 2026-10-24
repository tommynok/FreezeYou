package cf.playhi.freezeyou.utils

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.inputmethod.InputMethodManager

/**
 * Packages whose freezing leaves the device hard to recover from: no status bar or navigation,
 * no Settings to undo it with, no keyboard to type with, or no system core at all. Freezing them
 * is still the user's call — these are only used to ask for confirmation before a bulk action,
 * not to forbid anything. The launcher is deliberately not here: alternative launchers are a
 * normal thing to freeze, and that choice belongs to the user.
 */
object CriticalPackagesUtils {

    private const val ANDROID_SYSTEM = "android"
    private const val AOSP_SYSTEM_UI = "com.android.systemui"
    private const val AOSP_SETTINGS = "com.android.settings"

    /**
     * Critical on every device, whatever the OEM: the system image itself and the status bar with
     * navigation, which is what a user would reach for to undo a freeze.
     */
    @JvmStatic
    fun alwaysCriticalPackages(): Set<String> = setOf(ANDROID_SYSTEM, AOSP_SYSTEM_UI)

    /**
     * @return the subset of [packages] considered critical, in the order given.
     */
    @JvmStatic
    fun findCriticalPackages(context: Context, packages: Collection<String>): List<String> {
        return findCriticalPackages(packages, buildCriticalSet(context))
    }

    /**
     * The decision itself, with the two device-dependent names passed in rather than resolved:
     * a plain JVM test can check it that way, the same as with FUFUtils.normalizeSelectedTarget.
     *
     * @return the subset of [packages] present in [critical], in the order given.
     */
    @JvmStatic
    fun findCriticalPackages(packages: Collection<String>, critical: Set<String>): List<String> {
        return packages.filter { it in critical }
    }

    private fun buildCriticalSet(context: Context): Set<String> {
        val critical = alwaysCriticalPackages().toMutableSet()
        // Resolved rather than hardcoded: OEM builds ship Settings and the keyboard under their
        // own package names, and a stale hardcoded list would silently protect nothing.
        resolveSettingsPackage(context)?.let { critical.add(it) }
        critical.addAll(resolveInputMethodPackages(context))
        return critical
    }

    private fun resolveSettingsPackage(context: Context): String? {
        return try {
            Intent(Settings.ACTION_SETTINGS)
                .resolveActivity(context.packageManager)
                ?.packageName
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } ?: AOSP_SETTINGS
    }

    internal fun resolveInputMethodPackages(context: Context): Set<String> {
        val result = mutableSetOf<String>()

        // 1. Resolve from InputMethodManager (standard public framework API available across all Android versions
        // without requiring any special permissions, reliably returning all active/enabled keyboards).
        try {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.enabledInputMethodList?.forEach { imi ->
                imi.packageName?.takeIf { it.isNotBlank() }?.let { result.add(it) }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Resolve default IME from Settings.Secure
        try {
            val raw = Settings.Secure.getString(
                context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD
            )
            parsePackageNameFromImeId(raw)?.let { result.add(it) }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 3. Fallback: query default IME via Shizuku shell if list is empty and Shizuku is available
        // (Root su is deliberately omitted here to prevent blocking UI thread with interactive prompt)
        if (result.isEmpty()) {
            try {
                if (PrivilegedShellUtils.isShizukuAvailable()) {
                    val res = PrivilegedShellUtils.runShizukuCommand("settings get secure default_input_method", 1000L)
                    if (res.isSuccessful) {
                        parsePackageNameFromImeId(res.output)?.let { result.add(it) }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return result
    }

    internal fun parsePackageNameFromImeId(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val trimmed = raw.trim()
        if (trimmed.equals("null", ignoreCase = true)) return null
        val pkg = trimmed.substringBefore('/').trim()
        return if (pkg.isNotEmpty() && !pkg.equals("null", ignoreCase = true)) pkg else null
    }
}
