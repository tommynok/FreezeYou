package cf.playhi.freezeyou.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import net.grandcentrix.tray.AppPreferences;

import java.util.Collections;

import cf.playhi.freezeyou.utils.NotificationUtils;

public class NotificationDeletedReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        AppPreferences appPreferences = new AppPreferences(context);
        String notifying = appPreferences.getString("notifying", "");
        String pkgName = intent.getStringExtra("pkgName");
        if (null != pkgName && !"".equals(pkgName) && notifying != null) {
            // Whole-entry removal, and a write only when the list actually changed: the old
            // replace(pkgName + ",", "") cut out a piece of any longer name that held it.
            String updated = NotificationUtils.removeFromNotifyingList(
                    notifying, Collections.singletonList(pkgName));
            if (!updated.equals(notifying)) {
                appPreferences.put("notifying", updated);
            }
        }
    }
}
