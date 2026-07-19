package cf.playhi.freezeyou.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import cf.playhi.freezeyou.R
import cf.playhi.freezeyou.utils.ApplicationInfoUtils
import cf.playhi.freezeyou.utils.NotificationUtils.deleteNotification
import cf.playhi.freezeyou.utils.OneKeyListUtils.removeFromOneKeyList
import java.io.File

class UninstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) {
            return
        }
        if (Intent.ACTION_PACKAGE_FULLY_REMOVED == intent.action) {
            var pkgName = intent.dataString
            if (pkgName != null) {
                pkgName = pkgName.replace("package:", "")
                if (ApplicationInfoUtils.getApplicationInfoFromPkgName(pkgName, context) == null) {
                    removeFromOneKeyList(
                        context,
                        context.getString(R.string.sAutoFreezeApplicationList),
                        pkgName
                    )
                    removeFromOneKeyList(
                        context,
                        context.getString(R.string.sOneKeyUFApplicationList),
                        pkgName
                    )
                    removeFromOneKeyList(
                        context,
                        context.getString(R.string.sFreezeOnceQuit),
                        pkgName
                    )
                    //清理被卸载应用程序的图标数据
                    val file = File(context.filesDir.toString() + "/icon/" + pkgName + ".png")
                    if (file.exists() && file.isFile) {
                        file.delete()
                    }
                    val file2 = File(context.cacheDir.toString() + "/icon/" + pkgName + ".png")
                    if (file2.exists() && file2.isFile) {
                        file2.delete()
                    }
                    //清理被卸载应用程序的名称
                    context.getSharedPreferences("NameOfPackages", Context.MODE_PRIVATE)
                        .edit().remove(pkgName).apply()
                    //清理可能存在的通知栏提示重新显示数据
                    deleteNotification(context, pkgName)
                }
            }
        }
    }
}
