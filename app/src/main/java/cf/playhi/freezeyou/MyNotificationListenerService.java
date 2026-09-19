package cf.playhi.freezeyou;

import android.annotation.TargetApi;
import android.content.Intent;
import android.os.IBinder;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.ArrayList;

import cf.playhi.freezeyou.utils.ProcessSharedState;

// Needs to be retained for compatibility
// with old FreezeYou structures and settings.
@TargetApi(21)
public class MyNotificationListenerService extends NotificationListenerService {

    private static StatusBarNotification[] statusBarNotifications = new StatusBarNotification[]{};
    private boolean mListenerConnected = false;

    @Override
    public void onListenerDisconnected() {
        super.onListenerDisconnected();
        mListenerConnected = false;
        statusBarNotifications = new StatusBarNotification[]{};
        publish();
    }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        mListenerConnected = true;
        statusBarNotifications = getActiveNotifications();
        publish();
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        super.onNotificationPosted(sbn);
        if (mListenerConnected) {
            statusBarNotifications = getActiveNotifications();
            publish();
        }
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        super.onNotificationRemoved(sbn);
        if (mListenerConnected) {
            statusBarNotifications = getActiveNotifications();
            publish();
        }
    }

    /**
     * This service runs in :backgroundService, so the array above is only visible here. The guard
     * that refuses to freeze an application while it is showing a notification runs wherever the
     * freeze was asked for, so the package names have to be put somewhere every process can read.
     */
    private void publish() {
        StatusBarNotification[] current = statusBarNotifications;
        ArrayList<String> pkgNames = new ArrayList<>();
        if (current != null) {
            for (StatusBarNotification sbn : current) {
                if (sbn != null && sbn.getPackageName() != null) {
                    pkgNames.add(sbn.getPackageName());
                }
            }
        }
        ProcessSharedState.setNotifyingPackages(pkgNames);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return super.onBind(intent);
    }

}
