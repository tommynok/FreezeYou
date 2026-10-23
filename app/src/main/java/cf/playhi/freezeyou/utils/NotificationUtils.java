package cf.playhi.freezeyou.utils;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import cf.playhi.freezeyou.Freeze;
import cf.playhi.freezeyou.Main;
import cf.playhi.freezeyou.R;
import cf.playhi.freezeyou.receiver.NotificationDeletedReceiver;
import cf.playhi.freezeyou.service.FUFService;
import cf.playhi.freezeyou.storage.mmkv.DefaultMultiProcessMMKVStorage;

import static cf.playhi.freezeyou.storage.key.DefaultMultiProcessMMKVStorageBooleanKeys.combineBatchQuickNotification;
import static cf.playhi.freezeyou.storage.key.DefaultMultiProcessMMKVStorageBooleanKeys.createQuickFUFNotiAfterUnfrozen;
import static cf.playhi.freezeyou.storage.key.DefaultMultiProcessMMKVStorageBooleanKeys.notificationBarDisableClickDisappear;
import static cf.playhi.freezeyou.storage.key.DefaultMultiProcessMMKVStorageBooleanKeys.notificationBarDisableSlideOut;
import static cf.playhi.freezeyou.storage.key.DefaultMultiProcessMMKVStorageBooleanKeys.notificationBarFreezeImmediately;
import static cf.playhi.freezeyou.utils.ApplicationLabelUtils.getApplicationLabel;
import static cf.playhi.freezeyou.utils.ToastUtils.showToast;

public final class NotificationUtils {

    /**
     * The packages the app currently has a quick freeze notification out for. The boot receiver
     * re-posts the notifications for the apps still unfrozen and the manage screen lists them, both
     * after a process that created them is long gone, so the list lives in the multi-process store
     * rather than in memory.
     *
     * It is not what {@code avoidFreezeNotifyingApplications} consults; that asks the notification
     * listener what it is seeing right now.
     *
     * Public because the one-time Tray migration copies this exact key and must not guess its name.
     */
    public static final String NOTIFYING = "notifying";
    private static final String NOTIFYING_VERSION = "notifying_version";

    /**
     * Fixed, because it belongs to no package: one card describes a whole batch, and it has to be
     * replaced and cancelled by the id alone rather than by a hash of anything in the list.
     */
    public static final int BATCH_NOTIFICATION_ID = 210101;

    private static final String QUICK_FUF_CHANNEL_ID = "FAUf";

    /**
     * Serialises the read-modify-write of {@link #NOTIFYING} inside this process, used in tandem with
     * MMKV's cross-process file lock (storage.lock()). NOTIFYING_LOCK serialises threads within the same
     * JVM, while storage.lock() coordinates with other processes (e.g. :backgroundService).
     */
    private static final Object NOTIFYING_LOCK = new Object();

    public static void createFUFQuickNotification(Context context, String pkgName, int iconResId, Bitmap bitmap) {
        boolean freezeImmediately = notificationBarFreezeImmediately.getValue(null);
        String description = freezeImmediately ?
                context.getString(R.string.freezeImmediately) : context.getString(R.string.disableAEnable);
        Notification.Builder mBuilder = new Notification.Builder(context);
        int mId = pkgName.hashCode();
        String name = getApplicationLabel(context, null, null, pkgName);
        if (!context.getString(R.string.uninstalled).equals(name)) {
            mBuilder.setSmallIcon(iconResId);
            mBuilder.setLargeIcon(bitmap);
            mBuilder.setContentTitle(name);
            mBuilder.setContentText(description);
            mBuilder.setAutoCancel(!notificationBarDisableClickDisappear.getValue(null));
            mBuilder.setOngoing(notificationBarDisableSlideOut.getValue(null));

            Intent intent = new Intent(context, NotificationDeletedReceiver.class).putExtra("pkgName", pkgName);
            PendingIntent pendingIntent =
                    PendingIntent.getBroadcast(context, mId, intent,
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                                    ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                                    : PendingIntent.FLAG_UPDATE_CURRENT);
            mBuilder.setDeleteIntent(pendingIntent);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
                if (notificationManager != null) {
                    ensureQuickFufChannel(context, notificationManager);
                }
                mBuilder.setChannelId(QUICK_FUF_CHANNEL_ID);
            }
            // Create an Intent for the activity you want to start
            Intent resultIntent;
            PendingIntent resultPendingIntent;
            int pendingIntentFlags =
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                            ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                            : PendingIntent.FLAG_UPDATE_CURRENT;
            if (freezeImmediately) {
                resultIntent = new Intent(context, FUFService.class)
                        .putExtra("pkgName", pkgName)
                        .putExtra("single", true)
                        .putExtra("freeze", true);
                resultPendingIntent = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                        ? PendingIntent.getForegroundService(context, mId, resultIntent, pendingIntentFlags)
                        : PendingIntent.getService(context, mId, resultIntent, pendingIntentFlags);
            } else {
                resultIntent = new Intent(context, Freeze.class).putExtra("pkgName", pkgName).putExtra("fromShortcut", false);
                resultPendingIntent =
                        PendingIntent.getActivity(
                                context, mId, resultIntent, pendingIntentFlags);
            }
            mBuilder.setContentIntent(resultPendingIntent);
            NotificationManager mNotificationManager =
                    (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (mNotificationManager != null) {
                mNotificationManager.notify(mId, mBuilder.build());
                synchronized (NOTIFYING_LOCK) {
                    DefaultMultiProcessMMKVStorage storage = new DefaultMultiProcessMMKVStorage();
                    storage.lock();
                    try {
                        String notifying = storage.getString(NOTIFYING, "");
                        String updated = addToNotifyingList(notifying, pkgName);
                        if (!updated.equals(notifying)) {
                            long targetVersion = storage.getLong(NOTIFYING_VERSION, 0L) + 1;
                            storage.putString(NOTIFYING, updated)
                                    .putLong(NOTIFYING_VERSION, targetVersion)
                                    .sync();
                        }
                    } finally {
                        storage.unlock();
                    }
                }
            }
        }
    }

    public static void deleteNotification(Context context, String pkgName) {
        deleteNotifications(context, Collections.singletonList(pkgName));
    }

    /**
     * The packages the app has a quick freeze notification out for, in the order they were added.
     *
     * Read by the boot receiver and the manage screen, both of which want the names rather than the
     * stored string, so the splitting lives here once.
     */
    public static List<String> getNotifyingPackages() {
        return Arrays.asList(PackageListUtils.parse(
                new DefaultMultiProcessMMKVStorage().getString(NOTIFYING, "")));
    }

    /**
     * Cancelling a notification is a cheap binder call, but the list of notifying packages is one
     * string in the multi-process store, and rewriting it costs a sync and a broadcast each. Doing
     * that per application made a batch of 25 spend a few hundred milliseconds on one string, so the
     * whole batch is taken out of that string in a single pass.
     */
    public static void deleteNotifications(Context context, List<String> pkgNames) {
        if (pkgNames.isEmpty()) {
            return;
        }
        NotificationManager mNotificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (mNotificationManager == null) {
            return;
        }
        for (String pkgName : pkgNames) {
            mNotificationManager.cancel(pkgName.hashCode());
        }
        deleteNotifying(context, pkgNames);
    }

    /**
     * Take the packages out of the stored list without touching their notifications, for the case
     * where there is no notification left to cancel: the user dismissed it and the system told us.
     */
    public static void clearNotifyingRecord(Context context, String pkgName) {
        deleteNotifying(context, Collections.singletonList(pkgName));
    }

    /**
     * Take the packages out of the stored list without touching their notifications, for the case
     * where the notification is already gone: the user dismissed it, the system told us, or the boot
     * receiver found the application frozen already and so nothing out for it to cancel.
     *
     * Public because the boot receiver clears a whole batch of them at once, and {@code
     * clearNotifyingRecord} one at a time would rewrite the stored string once per application.
     *
     * The card still standing is rebuilt from what is left rather than from what was just taken out,
     * because one application freezing out of a batch of ten leaves nine to describe. That only
     * happens with the batch card switched on: by default the applications left behind each still
     * hold a notification of their own, so the list simply gets shorter.
     */
    public static void deleteNotifying(Context context, List<String> pkgNames) {
        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        String singleSurvivor = null;
        List<String> survivors = null;
        List<String> restoreIndividual = null;
        long targetVersion = 0;
        boolean cancelBatch = false;
        boolean createQuick = createQuickFUFNotiAfterUnfrozen.getValue(null);
        boolean combineBatch = combineBatchQuickNotification.getValue(null);
        synchronized (NOTIFYING_LOCK) {
            DefaultMultiProcessMMKVStorage storage = new DefaultMultiProcessMMKVStorage();
            storage.lock();
            try {
                String notifying = storage.getString(NOTIFYING, "");
                String updated = removeFromNotifyingList(notifying, pkgNames);
                if (!updated.equals(notifying)) {
                    targetVersion = storage.getLong(NOTIFYING_VERSION, 0L) + 1;
                    storage.putString(NOTIFYING, updated)
                            .putLong(NOTIFYING_VERSION, targetVersion)
                            .sync();
                } else {
                    targetVersion = storage.getLong(NOTIFYING_VERSION, 0L);
                }
                List<String> remaining = Arrays.asList(PackageListUtils.parse(updated));
                if (notificationManager != null) {
                    if (!createQuick || remaining.isEmpty()) {
                        cancelBatch = true;
                    } else if (!combineBatch) {
                        cancelBatch = true;
                        restoreIndividual = remaining;
                    } else if (remaining.size() == 1) {
                        cancelBatch = true;
                        singleSurvivor = remaining.get(0);
                    } else {
                        survivors = remaining;
                    }
                }
            } finally {
                storage.unlock();
            }
            if (cancelBatch && notificationManager != null) {
                notificationManager.cancel(BATCH_NOTIFICATION_ID);
            }
        }
        if (restoreIndividual != null) {
            for (String pkg : restoreIndividual) {
                FUFUtils.checkAndCreateFUFQuickNotification(context, pkg);
            }
        } else if (singleSurvivor != null) {
            FUFUtils.checkAndCreateFUFQuickNotification(context, singleSurvivor);
        } else if (survivors != null && notificationManager != null) {
            postBatchQuickNotification(context, notificationManager, survivors, targetVersion);
        }
    }

    /**
     * One standalone card for the applications currently notified about: the count on the collapsed
     * line, the names when it is expanded, and one action that freezes the lot.
     *
     * Only posted when {@code combineBatchQuickNotification} is on. The default is the stock
     * behaviour — one notification per application, each with its own icon, which SystemUI stacks by
     * itself — and this card replaces that with a single row for the user who would rather have one
     * "freeze all" than a dozen.
     *
     * This is what a group was tried for and is simpler than it. A group is subject to SystemUI's own
     * arithmetic - a header's action buttons get dropped and the children it shows are capped - while
     * one ordinary notification of our own keeps its buttons and lists the applications in its text.
     *
     * @param pkgNames the applications to put on the stored list. The card itself is built from
     *                 whatever the list holds afterwards, so a second batch adds to the card instead
     *                 of taking the first batch off it.
     */
    public static void createBatchQuickNotification(Context context, List<String> pkgNames) {
        if (pkgNames == null || pkgNames.isEmpty()) {
            return;
        }
        // Checked here and not only where an unfreeze asks for the card, because a removal from the
        // notifying list rebuilds this card too: with either setting off it has to stay off whichever
        // way the list moved.
        if (!createQuickFUFNotiAfterUnfrozen.getValue(null)) {
            return;
        }
        boolean combineBatch = combineBatchQuickNotification.getValue(null);
        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager == null) {
            return;
        }
        List<String> allPackages;
        long targetVersion;
        synchronized (NOTIFYING_LOCK) {
            DefaultMultiProcessMMKVStorage storage = new DefaultMultiProcessMMKVStorage();
            storage.lock();
            try {
                String notifying = storage.getString(NOTIFYING, "");
                String updated = addBatchToNotifyingList(notifying, pkgNames);
                if (!updated.equals(notifying)) {
                    targetVersion = storage.getLong(NOTIFYING_VERSION, 0L) + 1;
                    storage.putString(NOTIFYING, updated)
                            .putLong(NOTIFYING_VERSION, targetVersion)
                            .sync();
                } else {
                    targetVersion = storage.getLong(NOTIFYING_VERSION, 0L);
                }
                allPackages = Arrays.asList(PackageListUtils.parse(updated));
            } finally {
                storage.unlock();
            }
        }
        if (!combineBatch) {
            notificationManager.cancel(BATCH_NOTIFICATION_ID);
            for (String pkg : allPackages) {
                FUFUtils.checkAndCreateFUFQuickNotification(context, pkg);
            }
            return;
        }
        if (allPackages.size() == 1) {
            notificationManager.cancel(BATCH_NOTIFICATION_ID);
            FUFUtils.checkAndCreateFUFQuickNotification(context, allPackages.get(0));
            return;
        }
        postBatchQuickNotification(context, notificationManager, allPackages, targetVersion);
    }

    private static final ExecutorService NOTIFICATION_RECONCILE_EXECUTOR =
            Executors.newSingleThreadExecutor(new ThreadFactory() {
                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "NotificationReconcile");
                    t.setDaemon(true);
                    return t;
                }
            });

    public static void reconcileQuickNotifications(final Context context) {
        if (context == null) {
            return;
        }
        final Context appContext = context.getApplicationContext();
        NOTIFICATION_RECONCILE_EXECUTOR.execute(new Runnable() {
            @Override
            public void run() {
                NotificationManager notificationManager =
                        (NotificationManager) appContext.getSystemService(Context.NOTIFICATION_SERVICE);
                if (notificationManager == null) {
                    return;
                }
                boolean createQuick = createQuickFUFNotiAfterUnfrozen.getValue(null);
                boolean combineBatch = combineBatchQuickNotification.getValue(null);
                List<String> allPackages;
                long targetVersion;
                synchronized (NOTIFYING_LOCK) {
                    DefaultMultiProcessMMKVStorage storage = new DefaultMultiProcessMMKVStorage();
                    storage.lock();
                    try {
                        String notifying = storage.getString(NOTIFYING, "");
                        allPackages = Arrays.asList(PackageListUtils.parse(notifying));
                        targetVersion = storage.getLong(NOTIFYING_VERSION, 0L);
                    } finally {
                        storage.unlock();
                    }
                }
                if (!createQuick || allPackages.isEmpty()) {
                    notificationManager.cancel(BATCH_NOTIFICATION_ID);
                    for (String pkg : allPackages) {
                        notificationManager.cancel(pkg.hashCode());
                    }
                } else if (combineBatch) {
                    if (allPackages.size() > 1) {
                        postBatchQuickNotification(appContext, notificationManager, allPackages, targetVersion);
                    } else {
                        notificationManager.cancel(BATCH_NOTIFICATION_ID);
                        FUFUtils.checkAndCreateFUFQuickNotification(appContext, allPackages.get(0));
                    }
                } else {
                    notificationManager.cancel(BATCH_NOTIFICATION_ID);
                    for (String pkg : allPackages) {
                        FUFUtils.checkAndCreateFUFQuickNotification(appContext, pkg);
                    }
                }
            }
        });
    }

    private static void postBatchQuickNotification(
            Context context, NotificationManager notificationManager, List<String> allPackages, long expectedVersion) {
        if (allPackages == null || allPackages.isEmpty()) {
            notificationManager.cancel(BATCH_NOTIFICATION_ID);
            return;
        }

        String content = context.getString(R.string.quickFUFGroupSummaryContent, allPackages.size());
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ensureQuickFufChannel(context, notificationManager);
            builder = new Notification.Builder(context, QUICK_FUF_CHANNEL_ID);
        } else {
            builder = new Notification.Builder(context);
        }
        builder.setSmallIcon(R.drawable.ic_notification);
        builder.setContentTitle(context.getString(R.string.app_name));
        builder.setContentText(content);
        builder.setAutoCancel(!notificationBarDisableClickDisappear.getValue(null));
        builder.setOngoing(notificationBarDisableSlideOut.getValue(null));

        // A batch of thirty names is a wall of text nobody reads on a lock screen, so the card
        // lists the first few and says how many more there are. The count is the whole list
        // either way, and freeze-all still takes the whole list.
        final int maxNamesShown = 6;
        StringBuilder names = new StringBuilder();
        for (int i = 0; i < allPackages.size() && i < maxNamesShown; i++) {
            if (names.length() > 0) {
                names.append("\n");
            }
            names.append("• ").append(getApplicationLabel(context, null, null, allPackages.get(i)));
        }
        if (allPackages.size() > maxNamesShown) {
            names.append("\n").append(context.getString(
                    R.string.andMoreApplications, allPackages.size() - maxNamesShown));
        }
        Notification.BigTextStyle bigTextStyle = new Notification.BigTextStyle()
                .setBigContentTitle(content)
                .bigText(names);
        builder.setStyle(bigTextStyle);

        int pendingIntentFlags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                : PendingIntent.FLAG_UPDATE_CURRENT;
        String[] packages = allPackages.toArray(new String[0]);

        Intent freezeAllIntent = new Intent(context, FUFService.class)
                .putExtra("freeze", true)
                .putExtra("packages", packages);
        PendingIntent freezeAllPendingIntent = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? PendingIntent.getForegroundService(context, BATCH_NOTIFICATION_ID, freezeAllIntent, pendingIntentFlags)
                : PendingIntent.getService(context, BATCH_NOTIFICATION_ID, freezeAllIntent, pendingIntentFlags);
        builder.addAction(R.drawable.ic_notification,
                context.getString(R.string.freezeAll), freezeAllPendingIntent);

        // Tapping the card opens FreezeYou, whichever way the single-application notifications
        // behave. Freezing the lot belongs to the action button alone: the body of a card
        // naming two dozen applications is far too easy to brush against, and what it would do
        // is freeze all of them.
        PendingIntent mainPendingIntent = PendingIntent.getActivity(
                context, BATCH_NOTIFICATION_ID, new Intent(context, Main.class), pendingIntentFlags);
        builder.setContentIntent(mainPendingIntent);

        Intent deletedIntent = new Intent(context, NotificationDeletedReceiver.class)
                .putExtra("packages", packages);
        PendingIntent deletedPendingIntent = PendingIntent.getBroadcast(
                context, BATCH_NOTIFICATION_ID, deletedIntent, pendingIntentFlags);
        builder.setDeleteIntent(deletedPendingIntent);

        synchronized (NOTIFYING_LOCK) {
            if (expectedVersion > 0 && new DefaultMultiProcessMMKVStorage().getLong(NOTIFYING_VERSION, 0L) != expectedVersion) {
                return;
            }

            // Once one card names the whole list, the per-application cards for those same
            // applications say the same thing twice - and the batch card is the one that keeps the
            // freeze-all action, so it is the individual ones that go.
            for (String pkgName : allPackages) {
                notificationManager.cancel(pkgName.hashCode());
            }
            notificationManager.notify(BATCH_NOTIFICATION_ID, builder.build());
        }
    }

    /**
     * The channel the quick freeze notifications share, whether one is posted for a single
     * application or one for a batch. Creation is idempotent, so either side can ask for it without
     * caring which one runs first.
     */
    private static void ensureQuickFufChannel(Context context, NotificationManager notificationManager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        String description = notificationBarFreezeImmediately.getValue(null)
                ? context.getString(R.string.freezeImmediately)
                : context.getString(R.string.disableAEnable);
        NotificationChannel channel = new NotificationChannel(
                QUICK_FUF_CHANNEL_ID, description, NotificationManager.IMPORTANCE_LOW);
        channel.setDescription(description);
        // Register the channel with the system; you can't change the importance
        // or other notification behaviors after this
        notificationManager.createNotificationChannel(channel);
    }

    /**
     * @return every name on the list, each followed by a comma, with {@code pkgName} appended last
     *         unless it is already there or blank.
     */
    public static String addToNotifyingList(String notifying, String pkgName) {
        return addBatchToNotifyingList(notifying, Collections.singletonList(pkgName));
    }

    /**
     * Put every one of these packages on the list of applications currently showing a notification,
     * and return the list rebuilt.
     *
     * This used to be {@code notifying + pkgName + ","} guarded by {@code notifying.contains(pkgName
     * + ",")}, and the two halves were wrong in opposite directions: concatenating onto a list that
     * does not end in a comma stored one glued name belonging to no app, while the substring test
     * could find "com.foo," inside "xcom.foo," and then refuse to add a package that really is
     * notifying - so it stayed freezable with a notification on screen. Comparing whole parsed
     * entries fixes both, and an add that changes nothing no longer rewrites the preference.
     *
     * One pass over the batch rather than one rewrite per application, because a batch card names
     * them all at once and each rewrite of the stored string costs a sync and a broadcast.
     *
     * @return every name on the list, each followed by a comma, with the new ones appended last in
     *         the order they were given.
     */
    public static String addBatchToNotifyingList(String notifying, List<String> pkgNames) {
        ArrayList<String> result = new ArrayList<>();
        for (String candidate : PackageListUtils.parse(notifying)) {
            result.add(candidate);
        }
        for (String pkgName : pkgNames) {
            String name = pkgName == null ? "" : pkgName.trim();
            if (!name.isEmpty() && !result.contains(name)) {
                result.add(name);
            }
        }
        return joinWithCommas(result);
    }

    /**
     * The list is stored as one string, each package followed by a comma. Separated from the store
     * access above so it can be tested without a device: this is the part that a batch made
     * interesting, and getting it wrong would quietly leave packages marked as notifying, which is
     * what stops them being frozen.
     *
     * Removal now compares whole parsed entries instead of cutting {@code pkgName + ","} out with
     * String.replace, which also matched inside a longer name: dropping "com.foo" out of "xcom.foo,"
     * used to leave "x," as a package that is still notifying.
     *
     * @return the list without any of {@code pkgNames}, each remaining name followed by a comma.
     */
    public static String removeFromNotifyingList(String notifying, List<String> pkgNames) {
        if (pkgNames.isEmpty()) {
            // The same instance, on purpose: nothing was rebuilt, so the caller that compares
            // before writing to the store sees no change and skips the sync and the broadcast.
            return notifying;
        }

        ArrayList<String> leaving = new ArrayList<>(pkgNames.size());
        for (String pkgName : pkgNames) {
            if (pkgName != null && !pkgName.trim().isEmpty()) {
                leaving.add(pkgName.trim());
            }
        }

        ArrayList<String> kept = new ArrayList<>();
        for (String candidate : PackageListUtils.parse(notifying)) {
            if (!leaving.contains(candidate)) {
                kept.add(candidate);
            }
        }
        return joinWithCommas(kept);
    }

    private static String joinWithCommas(Iterable<String> names) {
        StringBuilder result = new StringBuilder();
        for (String name : names) {
            result.append(name).append(",");
        }
        return result.toString();
    }

    public static void startAppNotificationSettingsSystemActivity(Activity activity, String pkgName, int pkgUid) {
        final Intent intent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            intent = new Intent("android.settings.APP_NOTIFICATION_SETTINGS");
            intent.putExtra("app_package", pkgName);
            intent.putExtra("app_uid", pkgUid);
            intent.putExtra("android.provider.extra.APP_PACKAGE", pkgName);
        } else {
            intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            Uri uri = Uri.parse("package:" + pkgName);
            intent.setData(uri);
        }
        try {
            activity.startActivity(intent);
        } catch (Exception e) {
            e.printStackTrace();
            showToast(activity, e.getLocalizedMessage());
        }
    }

}
