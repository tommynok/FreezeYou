package cf.playhi.freezeyou.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import cf.playhi.freezeyou.utils.TasksUtils;

public class TimeChangedReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return;
        }

        final String action = intent.getAction();
        if (Intent.ACTION_TIMEZONE_CHANGED.equals(action) || Intent.ACTION_TIME_CHANGED.equals(action)) {
            final PendingResult pendingResult = goAsync();
            final Context appContext = context.getApplicationContext();
            new Thread(() -> {
                try {
                    TasksUtils.checkTimeTasks(appContext);
                } catch (Throwable t) {
                    t.printStackTrace();
                } finally {
                    pendingResult.finish();
                }
            }, "TimeChangedWorker").start();
        }
    }
}
