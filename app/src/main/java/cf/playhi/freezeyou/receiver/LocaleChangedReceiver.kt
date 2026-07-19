package cf.playhi.freezeyou.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

class LocaleChangedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) {
            return
        }
        if (Intent.ACTION_LOCALE_CHANGED == intent.action) {
            val packageManager = context.packageManager
            val applicationInfo =
                packageManager.getInstalledApplications(PackageManager.GET_UNINSTALLED_PACKAGES)
            //更新所有应用程序名称
            for (info in applicationInfo) {
                context.getSharedPreferences("NameOfPackages", Context.MODE_PRIVATE)
                    .edit()
                    .putString(
                        info.packageName,
                        context.packageManager
                            .getApplicationLabel(info).toString()
                    )
                    .apply()
            }
        }
    }
}
