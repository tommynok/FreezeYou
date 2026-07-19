package cf.playhi.freezeyou.utils

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import cf.playhi.freezeyou.R

object ApplicationLabelUtils {
    @JvmStatic
    fun getApplicationLabel(
        context: Context,
        packageManager: PackageManager?,
        applicationInfo: ApplicationInfo?,
        pkgName: String?
    ): String {
        if (pkgName == null) {
            return ""
        }
        val sharedPreferences = context.getSharedPreferences("NameOfPackages", Context.MODE_PRIVATE)
        var name = sharedPreferences.getString(pkgName, "") ?: ""
        if ("" != name) {
            return name
        }
        val pm = packageManager ?: context.packageManager
        if (applicationInfo != null) {
            name = applicationInfo.loadLabel(pm).toString()
            sharedPreferences.edit().putString(pkgName, name).apply()
            return name
        } else {
            return try {
                name = pm.getApplicationInfo(pkgName, PackageManager.GET_UNINSTALLED_PACKAGES)
                    .loadLabel(pm).toString()
                sharedPreferences.edit().putString(pkgName, name).apply()
                name
            } catch (e: PackageManager.NameNotFoundException) {
                e.printStackTrace()
                context.getString(R.string.uninstalled)
            } catch (e: Exception) {
                e.printStackTrace()
                pkgName
            }
        }
    }
}
