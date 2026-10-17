package cf.playhi.freezeyou.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import cf.playhi.freezeyou.R;
import cf.playhi.freezeyou.app.FreezeYouBaseService;
import cf.playhi.freezeyou.listener.ScreenLockListener;
import cf.playhi.freezeyou.ui.SettingsActivity;

import static cf.playhi.freezeyou.storage.key.DefaultMultiProcessMMKVStorageBooleanKeys.onekeyFreezeWhenLockScreen;
import static cf.playhi.freezeyou.storage.key.DefaultMultiProcessMMKVStorageBooleanKeys.useForegroundService;

public class ScreenLockOneKeyFreezeService extends FreezeYouBaseService {

    /**
     * TriggerTasksService lives in this same :backgroundService process and posts its own
     * foreground notification under id 1. Two services sharing a notification id meant that
     * whichever of them stopped first took the other one's notification away with it, leaving a
     * running foreground service invisible.
     */
    private static final int NOTIFICATION_ID = 1001;
    private static final String CHANNEL_ID = "BackgroundService";

    private ScreenLockListener screenLockListener;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        postForegroundNotification();

        if (!onekeyFreezeWhenLockScreen.getValue(null)) {
            // Nothing is supposed to happen when the screen goes off, so the service leaves.
            // Two ways reach this: a restored backup writes the keys directly, and no live
            // SettingsActivity is there to call stopService; and a sticky restart of a killed
            // process arrives with a null intent after the switch was turned off.
            // The notification is still posted first on purpose - every start comes through
            // ServiceUtils.startService -> startForegroundService, and a start that never reaches
            // startForeground crashes the process.
            removeForegroundNotification();
            stopSelf(startId);
            return START_NOT_STICKY;
        }

        if (screenLockListener == null) {
            screenLockListener = new ScreenLockListener(getApplicationContext());
            screenLockListener.registerListener();
        }
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        if (screenLockListener != null) {
            screenLockListener.unregisterListener();
            screenLockListener = null;
        }
        removeForegroundNotification();
        super.onDestroy();
    }

    /**
     * A bare "Background service" line told the user nothing about why something of theirs stays
     * running, so the text names the automation and the tap opens the switch that controls it.
     */
    private void postForegroundNotification() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O
                && !useForegroundService.getValue(null)) {
            return;
        }

        final Notification.Builder mBuilder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(
                        new NotificationChannel(
                                CHANNEL_ID, getString(R.string.backgroundService),
                                NotificationManager.IMPORTANCE_NONE)
                );
            }
            mBuilder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            mBuilder = new Notification.Builder(this);
        }

        mBuilder.setSmallIcon(R.drawable.ic_notification);
        mBuilder.setContentTitle(getString(R.string.screenLockFreezeNotiTitle));
        mBuilder.setContentText(getString(R.string.screenLockFreezeNotiText));
        mBuilder.setSubText(getString(R.string.screenLockFreezeNotiTapHint));
        mBuilder.setContentIntent(openAutomationPendingIntent());
        mBuilder.setOngoing(true);

        startForeground(NOTIFICATION_ID, mBuilder.build());
    }

    private PendingIntent openAutomationPendingIntent() {
        Intent resultIntent = new Intent(getApplicationContext(), SettingsActivity.class)
                .putExtra(
                        SettingsActivity.EXTRA_PREFERENCE_ROOT,
                        SettingsActivity.PREFERENCE_ROOT_AUTOMATION
                )
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return PendingIntent.getActivity(
                getApplicationContext(), NOTIFICATION_ID, resultIntent,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                        ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                        : PendingIntent.FLAG_UPDATE_CURRENT);
    }

    @SuppressWarnings("deprecation")
    private void removeForegroundNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE);
        } else {
            stopForeground(true);
        }
        NotificationManager notificationManager =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.cancel(NOTIFICATION_ID);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
