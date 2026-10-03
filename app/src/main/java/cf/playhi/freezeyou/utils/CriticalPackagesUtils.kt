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
