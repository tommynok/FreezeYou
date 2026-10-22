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

import cf.playhi.freezeyou.Freeze;
import cf.playhi.freezeyou.R;
import cf.playhi.freezeyou.receiver.NotificationDeletedReceiver;
import cf.playhi.freezeyou.service.FUFService;
import cf.playhi.freezeyou.storage.mmkv.DefaultMultiProcessMMKVStorage;
import cf.playhi.freezeyou.ui.FUFNotificationsManageActivity;

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

    /**
     * Without an explicit group, SystemUI decides on its own how to bundle same-channel
     * notifications, and a batch of 25 splits into two or four stacks that drift apart. Naming the
     * group, and posting a summary for it, takes that decision away from the heuristics.
     */
    public static final String GROUP_KEY_QUICK_FUF = "cf.playhi.freezeyou.QUICK_FUF_GROUP";

    /**
     * Fixed, because it belongs to no package: the summary has to be replaced and cancelled by the
     * id alone, and a hash of anything would change with the list it describes.
     */
    public static final int SUMMARY_NOTIFICATION_ID = 210101;

    private static final String QUICK_FUF_CHANNEL_ID = "FAUf";

    /**
     * A group of one needs no summary: with fewer than two children the stack is not a stack, and a
     * lone card on top of it is just an extra notification to swipe away.
     */
    private static final int SUMMARY_MIN_CHILDREN = 2;

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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
                mBuilder.setGroup(GROUP_KEY_QUICK_FUF);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Only the summary alerts, so a batch of 25 unfreezes sounds once instead of 25 times.
                mBuilder.setGroupAlertBehavior(Notification.GROUP_ALERT_SUMMARY);
            }

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
            if (freezeImmediately) {
                resultIntent = new Intent(context, FUFService.class)
                        .putExtra("pkgName", pkgName)
                        .putExtra("single", true)
                        .putExtra("freeze", true);
                resultPendingIntent =
                        PendingIntent.getService(
                                context, mId, resultIntent,
                                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                                        ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                                        : PendingIntent.FLAG_UPDATE_CURRENT);
            } else {
                resultIntent = new Intent(context, Freeze.class).putExtra("pkgName", pkgName).putExtra("fromShortcut", false);
                resultPendingIntent =
                        PendingIntent.getActivity(
                                context, mId, resultIntent,
                                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                                        ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                                        : PendingIntent.FLAG_UPDATE_CURRENT);
            }
            mBuilder.setContentIntent(resultPendingIntent);
            NotificationManager mNotificationManager =
                    (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (mNotificationManager != null) {
                mNotificationManager.notify(mId, mBuilder.build());
                DefaultMultiProcessMMKVStorage storage = new DefaultMultiProcessMMKVStorage();
                String notifying = storage.getString(NOTIFYING, "");
                String updated = addToNotifyingList(notifying, pkgName);
                if (!updated.equals(notifying)) {
                    storage.putString(NOTIFYING, updated).sync();
                }
                updateGroupSummaryNotification(context);
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
     * where the notification is already gone: the user dismissed it, and the system told us.
     */
    public static void clearNotifyingRecord(Context context, String pkgName) {
        deleteNotifying(context, Collections.singletonList(pkgName));
    }

    private static void deleteNotifying(Context context, List<String> pkgNames) {
        DefaultMultiProcessMMKVStorage storage = new DefaultMultiProcessMMKVStorage();
        String notifying = storage.getString(NOTIFYING, "");
        String updated = removeFromNotifyingList(notifying, pkgNames);
        if (!updated.equals(notifying)) {
            storage.putString(NOTIFYING, updated).sync();
        }
        // Both directions end here: a dismissed or frozen application takes its count off the
        // summary, and the last two taking their notifications with them leave no group to summarise.
        updateGroupSummaryNotification(context);
    }

    /**
     * The summary card that holds the quick freeze notifications together, with the number of
     * applications currently unfrozen and one action that freezes the whole lot.
     *
     * It is rebuilt from the stored list rather than from what the caller just changed, because it is
     * called both one application at a time and after a batch, and the list is the same in both
     * cases. With fewer than two children it is cancelled instead: a single notification needs no
     * group header, and leaving one behind would put a card on screen that describes nothing.
     */
    public static void updateGroupSummaryNotification(Context context) {
        List<String> notifying = getNotifyingPackages();
        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager == null) {
            return;
        }
        if (notifying.size() < SUMMARY_MIN_CHILDREN) {
            notificationManager.cancel(SUMMARY_NOTIFICATION_ID);
            return;
        }

        Notification.Builder summaryBuilder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ensureQuickFufChannel(context, notificationManager);
            summaryBuilder = new Notification.Builder(context, QUICK_FUF_CHANNEL_ID);
        } else {
            summaryBuilder = new Notification.Builder(context);
        }
        summaryBuilder.setSmallIcon(R.drawable.ic_notification);
        summaryBuilder.setContentTitle(context.getString(R.string.app_name));
        summaryBuilder.setContentText(
                context.getString(R.string.quickFUFGroupSummaryContent, notifying.size()));
        summaryBuilder.setAutoCancel(!notificationBarDisableClickDisappear.getValue(null));
        summaryBuilder.setOngoing(notificationBarDisableSlideOut.getValue(null));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
            summaryBuilder.setGroup(GROUP_KEY_QUICK_FUF);
            summaryBuilder.setGroupSummary(true);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // The children are silenced as the group alerts on the summary, so the summary has to
            // claim that alert itself or a batch makes no sound at all.
            summaryBuilder.setGroupAlertBehavior(Notification.GROUP_ALERT_SUMMARY);
        }

        Intent manageIntent = new Intent(context, FUFNotificationsManageActivity.class);
        PendingIntent managePendingIntent = PendingIntent.getActivity(
                context, SUMMARY_NOTIFICATION_ID, manageIntent,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                        ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                        : PendingIntent.FLAG_UPDATE_CURRENT);
        summaryBuilder.setContentIntent(managePendingIntent);

        Intent freezeAllIntent = new Intent(context, FUFService.class)
                .putExtra("freeze", true)
                .putExtra("packages", notifying.toArray(new String[0]));
        PendingIntent freezeAllPendingIntent = PendingIntent.getService(
                context, SUMMARY_NOTIFICATION_ID, freezeAllIntent,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                        ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                        : PendingIntent.FLAG_UPDATE_CURRENT);
        summaryBuilder.addAction(R.drawable.ic_notification,
                context.getString(R.string.freezeAll), freezeAllPendingIntent);

        notificationManager.notify(SUMMARY_NOTIFICATION_ID, summaryBuilder.build());
    }

    /**
     * The channel the quick freeze notifications and their summary share. Creation is idempotent, so
     * either side can ask for it without caring which one runs first.
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
     * Add one package to the list of applications currently showing a notification, and return the
     * list rebuilt.
     *
     * This used to be {@code notifying + pkgName + ","} guarded by {@code notifying.contains(pkgName
     * + ",")}, and the two halves were wrong in opposite directions: concatenating onto a list that
     * does not end in a comma stored one glued name belonging to no app, while the substring test
     * could find "com.foo," inside "xcom.foo," and then refuse to add a package that really is
     * notifying - so it stayed freezable with a notification on screen. Comparing whole parsed
     * entries fixes both, and an add that changes nothing no longer rewrites the preference.
     *
     * @return every name on the list, each followed by a comma, with {@code pkgName} appended last
     *         unless it is already there or blank.
     */
    public static String addToNotifyingList(String notifying, String pkgName) {
        String name = pkgName == null ? "" : pkgName.trim();
        ArrayList<String> result = new ArrayList<>();
        for (String candidate : PackageListUtils.parse(notifying)) {
            result.add(candidate);
        }
        if (!name.isEmpty() && !result.contains(name)) {
            result.add(name);
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
