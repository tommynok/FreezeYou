package cf.playhi.freezeyou.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import net.grandcentrix.tray.AppPreferences

class NotificationDeletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val appPreferences = AppPreferences(context)
        val notifying = appPreferences.getString("notifying", "")
        val pkgName = intent.getStringExtra("pkgName")
        if (!pkgName.isNullOrEmpty() && notifying != null) {
            appPreferences.put("notifying", notifying.replace("$pkgName,", ""))
        }
    }
}
