package cf.playhi.freezeyou.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import cf.playhi.freezeyou.storage.key.DefaultSharedPreferenceStorageBooleanKeys.cacheApplicationsIcons
import cf.playhi.freezeyou.utils.ApplicationIconUtils
import cf.playhi.freezeyou.utils.ApplicationInfoUtils
import java.io.File

class PackageReplacedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) {
            return
        }
        if (Intent.ACTION_PACKAGE_REPLACED == intent.action) {
            var pkgName = intent.dataString
            if (pkgName != null) {
                pkgName = pkgName.replace("package:", "")
                val applicationInfo =
                    ApplicationInfoUtils.getApplicationInfoFromPkgName(pkgName, context)
                if (applicationInfo != null) {
                    // 检查设置并更新应用程序图标数据
                    if (cacheApplicationsIcons.getValue(context)) {
                        val file2 = File(context.cacheDir.toString() + "/icon/" + pkgName + ".png")
                        if (file2.exists() && file2.isFile) {
                            file2.delete()
                        }
                        val file = File(context.filesDir.toString() + "/icon/" + pkgName + ".png")
                        if (file.exists() && file.isFile) {
                            file.delete()
                        }
                        ApplicationIconUtils
                            .getApplicationIcon(
                                context, pkgName,
                                applicationInfo, false, true
                            )
                    }
                    //更新应用程序名称
                    context.getSharedPreferences("NameOfPackages", Context.MODE_PRIVATE)
                        .edit()
                        .putString(
                            pkgName,
                            context.packageManager
                                .getApplicationLabel(applicationInfo).toString()
                        )
                        .apply()
                }
            }
        }
    }
}
