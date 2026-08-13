package cf.playhi.freezeyou.utils

import android.content.Context
import android.content.Intent
import android.provider.Settings

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

    /**
     * @return the subset of [packages] considered critical, in the order given.
     */
    @JvmStatic
    fun findCriticalPackages(context: Context, packages: Collection<String>): List<String> {
        val critical = buildCriticalSet(context)
        return packages.filter { it in critical }
    }

    private fun buildCriticalSet(context: Context): Set<String> {
        val critical = mutableSetOf(ANDROID_SYSTEM, AOSP_SYSTEM_UI)
        // Resolved rather than hardcoded: OEM builds ship Settings and the keyboard under their
        // own package names, and a stale hardcoded list would silently protect nothing.
        resolveSettingsPackage(context)?.let { critical.add(it) }
        resolveCurrentInputMethodPackage(context)?.let { critical.add(it) }
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
        }
    }

    private fun resolveCurrentInputMethodPackage(context: Context): String? {
        return try {
            // Stored as "package/.ServiceName".
            Settings.Secure.getString(
                context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD
            )?.substringBefore('/')?.takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
