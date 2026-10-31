package cf.playhi.freezeyou.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import cf.playhi.freezeyou.R;
import cf.playhi.freezeyou.app.FreezeYouBaseService;
import cf.playhi.freezeyou.utils.ForceStopUtils;

public class ForceStopService extends FreezeYouBaseService {

    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            // Restart after the process was killed: nothing to act on.
            stopSelf(startId);
            return super.onStartCommand(null, flags, startId);
        }
        Context context = getApplicationContext();

        String[] packages = intent.getStringArrayExtra("packages");
        mExecutor.execute(() -> {
            try {
                ForceStopUtils.forceStop(context, packages);
            } finally {
                stopSelf(startId);
            }
        });

        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null)
                notificationManager.createNotificationChannel(
                        new NotificationChannel(
                                "ForceStop", getString(R.string.forceStop), NotificationManager.IMPORTANCE_NONE
                        )
                );
            Notification.Builder mBuilder = new Notification.Builder(this, "ForceStop");
            mBuilder.setSmallIcon(R.drawable.ic_stat_logo);
            mBuilder.setContentText(getString(R.string.forceStop));
            startForeground(6, mBuilder.build());
        } else {
            startForeground(6, new Notification());
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mExecutor.shutdown();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
