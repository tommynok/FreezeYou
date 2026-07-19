package cf.playhi.freezeyou.utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import cf.playhi.freezeyou.R
import cf.playhi.freezeyou.utils.MoreUtils.requestOpenWebSite
import java.util.*

object VersionUtils {
    @JvmStatic
    fun getVersionCode(context: Context): Int {
        val packageManager = context.packageManager
        val packageName = context.packageName
        val flags = 0
        var packageInfo: PackageInfo? = null
        try {
            packageInfo = packageManager.getPackageInfo(packageName, flags)
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
        }
        return packageInfo?.versionCode ?: 0
    }

    @JvmStatic
    fun getVersionName(context: Context): String {
        val packageManager = context.packageManager
        val packageName = context.packageName
        val flags = 0
        var packageInfo: PackageInfo? = null
        try {
            packageInfo = packageManager.getPackageInfo(packageName, flags)
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
        }
        return packageInfo?.versionName ?: ""
    }

    @JvmStatic
    fun isGooglePlayVersion(context: Context): Boolean {
        return getVersionName(context).contains("gp")
    }

    @JvmStatic
    fun checkUpdate(activity: Activity) {
        //"https://play.google.com/store/apps/details?id=cf.playhi.freezeyou"
        //"https://freezeyou.playhi.net/checkupdate.php?v=" + getVersionCode(context)
        AlertDialogUtils.buildAlertDialog(
            activity,
            R.mipmap.ic_launcher_new_round,
            R.string.plsSelect,
            R.string.notice
        )
            .setPositiveButton(R.string.appStore) { dialogInterface, i ->
                val intent =
                    Intent(Intent.ACTION_VIEW, Uri.parse("market://details/?id=cf.playhi.freezeyou"))
                val title = activity.getString(R.string.plsSelect)
                val chooser = Intent.createChooser(intent, title)
                if (intent.resolveActivity(activity.packageManager) != null) {
                    activity.startActivity(chooser)
                }
            }
            .setNeutralButton(R.string.visitWebsite) { dialog, i ->
                requestOpenWebSite(
                    activity,
                    if (isGooglePlayVersion(activity)) "https://play.google.com/store/apps/details?id=cf.playhi.freezeyou" else "https://freezeyou.playhi.net/checkupdate.php?v=" + getVersionCode(
                        activity
                    )
                )
            }
            .show()
    }

    /**
     * @return Whether need to ask for checking update.
     */
    @JvmStatic
    fun isOutdated(context: Context): Boolean {
        return isOutdated(context.getSharedPreferences("Ver", Context.MODE_PRIVATE))
    }

    /**
     * @param sp SharedPreferences, name Ver.
     * @return Whether need to ask for checking update.
     */
    @JvmStatic
    fun isOutdated(sp: SharedPreferences): Boolean {
        return Date().time - sp.getLong("Time", 0L) > 5184000000L
    }
}
