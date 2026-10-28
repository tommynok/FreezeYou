package cf.playhi.freezeyou;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.os.Build;
import android.os.IBinder;

import cf.playhi.freezeyou.app.FreezeYouBaseService;
import cf.playhi.freezeyou.utils.TasksUtils;

import static android.content.Context.MODE_PRIVATE;
import static cf.playhi.freezeyou.storage.key.DefaultMultiProcessMMKVStorageBooleanKeys.useForegroundService;
import static cf.playhi.freezeyou.utils.TasksUtils.cancelAllUnexecutedDelayTasks;

public class TriggerTasksService extends FreezeYouBaseService {

    private TriggerScreenLockListener triggerScreenLockListener;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (triggerScreenLockListener == null) {
            triggerScreenLockListener = new TriggerScreenLockListener(getApplicationContext());
            triggerScreenLockListener.registerListener();
        }

        return START_STICKY;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                || useForegroundService.getValue(null)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationManager notificationManager = getSystemService(NotificationManager.class);
                if (notificationManager != null) {
                    notificationManager.createNotificationChannel(
                            new NotificationChannel(
                                    "BackgroundService", getString(R.string.backgroundService),
                                    NotificationManager.IMPORTANCE_NONE)
                    );
                }
                Notification.Builder mBuilder =
                        new Notification.Builder(this, "BackgroundService");
                mBuilder.setSmallIcon(R.drawable.ic_shortcut_logo);
                mBuilder.setContentText(getString(R.string.backgroundService));
                Intent resultIntent = new Intent(getApplicationContext(), Main.class);
                PendingIntent resultPendingIntent =
                        PendingIntent.getActivity(
                                getApplicationContext(), 1, resultIntent,
                                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                mBuilder.setContentIntent(resultPendingIntent);
                startForeground(1, mBuilder.build());
            } else {
                startForeground(1, new Notification());
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }


    @Override
    public void onDestroy() {
        if (triggerScreenLockListener != null) {
            triggerScreenLockListener.unregisterListener();
            triggerScreenLockListener = null;
        }
        stopForeground(true);
        super.onDestroy();
    }
}

class TriggerScreenLockListener {

    private final Context mContext;
    private final ScreenLockBroadcastReceiver mScreenLockReceiver;

    TriggerScreenLockListener(Context context) {
        mContext = context;
        mScreenLockReceiver = new ScreenLockBroadcastReceiver();
    }

    private static class ScreenLockBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            SQLiteDatabase db = null;
            Cursor cursor = null;
            boolean hasScreenTrigger = false;
            boolean scanSucceeded = false;
            try {
                db = context.openOrCreateDatabase("scheduledTriggerTasks", MODE_PRIVATE, null);
                cursor = db.query("tasks", null, null, null, null, null, null);
                if (cursor != null && cursor.moveToFirst()) {
                    for (int i = 0; i < cursor.getCount(); i++) {
                        int enabled = cursor.getInt(cursor.getColumnIndex("enabled"));
                        String tg = cursor.getString(cursor.getColumnIndex("tg"));
                        if (enabled == 1 && ("onScreenOn".equals(tg) || "onScreenOff".equals(tg))) {
                            hasScreenTrigger = true;
                            break;
                        }
                        cursor.moveToNext();
                    }
                    if (action != null) {
                        cursor.moveToFirst();
                        switch (action) {
                            case Intent.ACTION_SCREEN_OFF:
                                onActionScreenOnOff(context, cursor, false);
                                break;
                            case Intent.ACTION_SCREEN_ON:
                                onActionScreenOnOff(context, cursor, true);
                                break;
                            default:
                                break;
                        }
                    }
                }
                scanSucceeded = true;
            } catch (SQLiteException e) {
                e.printStackTrace();
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
                if (db != null) {
                    db.close();
                }
            }
            if (scanSucceeded && !hasScreenTrigger) {
                context.stopService(new Intent(context, TriggerTasksService.class));
            }
        }

        private static void onActionScreenOnOff(Context context, Cursor cursor, boolean screenOn) {
            cancelAllUnexecutedDelayTasks(context, screenOn ? "onScreenOff" : "onScreenOn");
            for (int i = 0; i < cursor.getCount(); i++) {
                String tg = cursor.getString(cursor.getColumnIndex("tg"));
                int enabled = cursor.getInt(cursor.getColumnIndex("enabled"));
                if (enabled == 1 && (screenOn ? "onScreenOn" : "onScreenOff").equals(tg)) {
                    String task = cursor.getString(cursor.getColumnIndex("task"));
                    if (task != null && !"".equals(task)) {
                        TasksUtils.runTask(task, context, screenOn ? "onScreenOn" : "onScreenOff");
                    }
                }
                cursor.moveToNext();
            }
        }
    }

    void registerListener() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        mContext.registerReceiver(mScreenLockReceiver, filter);
    }

    void unregisterListener() {
        mContext.unregisterReceiver(mScreenLockReceiver);
    }


}
