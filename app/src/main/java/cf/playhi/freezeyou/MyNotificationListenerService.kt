package cf.playhi.freezeyou

import android.annotation.TargetApi
import android.content.Intent
import android.os.IBinder
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

// Needs to be retained for compatibility
// with old FreezeYou structures and settings.
@TargetApi(21)
class MyNotificationListenerService : NotificationListenerService() {
    private var mListenerConnected = false
    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        mListenerConnected = false
        statusBarNotifications = arrayOf()
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        mListenerConnected = true
        statusBarNotifications = activeNotifications
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)
        if (mListenerConnected) statusBarNotifications = activeNotifications
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        super.onNotificationRemoved(sbn)
        if (mListenerConnected) statusBarNotifications = activeNotifications
    }

    override fun onBind(intent: Intent): IBinder? {
        return super.onBind(intent)
    }

    companion object {
        @JvmStatic
        var statusBarNotifications = arrayOf<StatusBarNotification>()
            private set
    }
}
