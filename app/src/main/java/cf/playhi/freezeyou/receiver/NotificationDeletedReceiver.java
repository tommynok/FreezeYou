package cf.playhi.freezeyou.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import cf.playhi.freezeyou.utils.NotificationUtils;

public class NotificationDeletedReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String pkgName = intent.getStringExtra("pkgName");
        if (pkgName != null && !pkgName.isEmpty()) {
            // The notification is already gone - the user dismissed it - so only the stored record
            // of it needs taking back out. NotificationUtils owns that list and its whole-entry
            // removal; the old replace(pkgName + ",", "") cut a piece out of any longer name holding
            // it.
            NotificationUtils.clearNotifyingRecord(context, pkgName);
        }
    }
}
