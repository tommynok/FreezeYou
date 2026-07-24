package cf.playhi.freezeyou.utils

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.IBinder
import cf.playhi.freezeyou.storage.key.DefaultMultiProcessMMKVStorageStringKeys.selectFUFMode
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.ShizukuProvider
import rikka.shizuku.SystemServiceHelper

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

    @JvmStatic
    fun getRunningPackages(context: Context): Set<String> {
        val apiMode = selectFUFMode.getValue()?.toIntOrNull() ?: return emptySet()
        return when (apiMode) {
            in ROOT_API_MODES -> ProcessUtils.getRootRunningPackages()
            in SHIZUKU_API_MODES -> getShizukuRunningPackages(context)
            else -> emptySet()
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun getShizukuRunningPackages(context: Context): Set<String> {
        try {
            if (Build.VERSION.SDK_INT < 23) return emptySet()

            if (!Shizuku.pingBinder()) {
                ShizukuProvider.requestBinderForNonProviderProcess(context)
                var waited = 0
                while (!Shizuku.pingBinder() && waited < 3000) {
                    Thread.sleep(50)
                    waited += 50
                }
                if (!Shizuku.pingBinder()) return emptySet()
            }

            val am = Class.forName("android.app.IActivityManager\$Stub")
                .getMethod("asInterface", IBinder::class.java)
                .invoke(null, ShizukuBinderWrapper(SystemServiceHelper.getSystemService("activity")))
                ?: return emptySet()

            val processes = am.javaClass.getMethod("getRunningAppProcesses").invoke(am)
                    as? List<ActivityManager.RunningAppProcessInfo> ?: return emptySet()

            val packages = mutableSetOf<String>()
            for (process in processes) {
                process.pkgList?.let { packages.addAll(it) }
            }
            return packages
        } catch (e: Exception) {
            e.printStackTrace()
            return emptySet()
        }
    }
}
