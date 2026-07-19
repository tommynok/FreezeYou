package cf.playhi.freezeyou.utils

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager

object ApplicationInfoUtils {
    @JvmStatic
    fun getApplicationInfoFromPkgName(pkgName: String?, context: Context): ApplicationInfo? {
        if (pkgName == null) return null
        var applicationInfo: ApplicationInfo? = null
        try {
            applicationInfo = context.packageManager.getApplicationInfo(
                pkgName,
                PackageManager.GET_UNINSTALLED_PACKAGES
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return applicationInfo
    }
}
