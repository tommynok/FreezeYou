package cf.playhi.freezeyou.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import cf.playhi.freezeyou.R;
import cf.playhi.freezeyou.app.FreezeYouBaseService;
import cf.playhi.freezeyou.utils.FUFUtils;

import static cf.playhi.freezeyou.fuf.FUFSinglePackage.API_FREEZEYOU_LEGACY_AUTO;
import static cf.playhi.freezeyou.fuf.FUFSinglePackage.API_FREEZEYOU_MROOT_DPM;
import static cf.playhi.freezeyou.fuf.FUFSinglePackage.API_FREEZEYOU_MROOT_PROFILE_OWNER;
import static cf.playhi.freezeyou.fuf.FUFSinglePackage.API_FREEZEYOU_ROOT_DISABLE_ENABLE;
import static cf.playhi.freezeyou.storage.key.DefaultMultiProcessMMKVStorageBooleanKeys.lesserToast;
import static cf.playhi.freezeyou.storage.key.DefaultMultiProcessMMKVStorageStringKeys.selectFUFMode;
import static cf.playhi.freezeyou.utils.DevicePolicyManagerUtils.isDeviceOwner;
import static cf.playhi.freezeyou.utils.DevicePolicyManagerUtils.isProfileOwner;
import static cf.playhi.freezeyou.utils.FUFUtils.checkMRootFrozen;
import static cf.playhi.freezeyou.utils.FUFUtils.checkRootFrozen;
import static cf.playhi.freezeyou.utils.FUFUtils.oneKeyAction;
import static cf.playhi.freezeyou.utils.FUFUtils.processAction;
import static cf.playhi.freezeyou.utils.FUFUtils.processMRootAction;
import static cf.playhi.freezeyou.utils.FUFUtils.processRootAction;

public class FUFService extends FreezeYouBaseService {

    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startServiceForeground();
        if (intent == null) {
            stopSelf(startId);
            return super.onStartCommand(null, flags, startId);
        }
        boolean freeze = intent.getBooleanExtra("freeze", false);
        Context context = getApplicationContext();
        int apiMode = Integer.parseInt(Objects.requireNonNull(selectFUFMode.getValue(null)));
        if (intent.getBooleanExtra("single", false)) {
            String pkgName = intent.getStringExtra("pkgName");
            String target = intent.getStringExtra("target");
            String tasks = intent.getStringExtra("tasks");
            boolean askRun = intent.getBooleanExtra("askRun", false);
            boolean runImmediately = intent.getBooleanExtra("runImmediately", false);
            mExecutor.execute(() -> {
                try {
                    if (apiMode == API_FREEZEYOU_LEGACY_AUTO) {
                        if (freeze) {
                            if (Build.VERSION.SDK_INT >= 21 && isDeviceOwner(context)) {
                                processMRootAction(
                                        context, pkgName, target, tasks, true, askRun,
                                        false, null, false,
                                        !lesserToast.getValue(null));
                            } else if (Build.VERSION.SDK_INT >= 21 && isProfileOwner(context)) {
                                processAction(context, pkgName, API_FREEZEYOU_MROOT_PROFILE_OWNER,
                                        false, !lesserToast.getValue(null), askRun, target, tasks,
                                        false, null, false);
                            } else {
                                processRootAction(pkgName, target, tasks, context, false, askRun,
                                        false, null, false,
                                        !lesserToast.getValue(null));
                            }
                        } else {
                            if (checkMRootFrozen(context, pkgName)) {
                                int dpmMode = isProfileOwner(context) && !isDeviceOwner(context)
                                        ? API_FREEZEYOU_MROOT_PROFILE_OWNER
                                        : API_FREEZEYOU_MROOT_DPM;
                                processAction(context, pkgName, dpmMode,
                                        true, !lesserToast.getValue(null), askRun, target, tasks,
                                        runImmediately, null, false);
                            } else {
                                processRootAction(pkgName, target, tasks, context, true, askRun,
                                        runImmediately, null, false,
                                        !lesserToast.getValue(null));
                            }
                        }
                    } else {
                        processAction(
                                context, pkgName, apiMode, !freeze,
                                !lesserToast.getValue(null),
                                askRun, target, tasks,
                                runImmediately, null, false
                        );
                    }
                } finally {
                    stopSelf(startId);
                }
            });
        } else {
            String[] packages = intent.getStringArrayExtra("packages");
            mExecutor.execute(() -> {
                try {
                    if (packages != null && packages.length > 0 && apiMode == API_FREEZEYOU_LEGACY_AUTO) {
                        if (freeze) {
                            if (Build.VERSION.SDK_INT >= 21 && isDeviceOwner(context)) {
                                oneKeyAction(context, true, packages, API_FREEZEYOU_MROOT_DPM);
                            } else if (Build.VERSION.SDK_INT >= 21 && isProfileOwner(context)) {
                                oneKeyAction(context, true, packages, API_FREEZEYOU_MROOT_PROFILE_OWNER);
                            } else {
                                oneKeyAction(context, true, packages, API_FREEZEYOU_ROOT_DISABLE_ENABLE);
                            }
                        } else {
                            if (Build.VERSION.SDK_INT >= 21 && (isDeviceOwner(context) || isProfileOwner(context))) {
                                int dpmMode = isProfileOwner(context) && !isDeviceOwner(context)
                                        ? API_FREEZEYOU_MROOT_PROFILE_OWNER
                                        : API_FREEZEYOU_MROOT_DPM;
                                if (!FUFUtils.checkRootPermission()) {
                                    List<String> dpmList = new ArrayList<>();
                                    List<String> rootList = new ArrayList<>();
                                    for (String pkg : packages) {
                                        if (checkMRootFrozen(context, pkg) || !checkRootFrozen(context, pkg, null)) {
                                            dpmList.add(pkg);
                                        } else {
                                            rootList.add(pkg);
                                        }
                                    }
                                    if (!dpmList.isEmpty()) {
                                        oneKeyAction(context, false, dpmList.toArray(new String[0]), dpmMode);
                                    }
                                    if (!rootList.isEmpty()) {
                                        oneKeyAction(context, false, rootList.toArray(new String[0]), API_FREEZEYOU_ROOT_DISABLE_ENABLE);
                                    }
                                } else {
                                    List<String> dpmList = new ArrayList<>();
                                    List<String> rootList = new ArrayList<>();
                                    for (String pkg : packages) {
                                        if (checkMRootFrozen(context, pkg)) {
                                            dpmList.add(pkg);
                                        } else {
                                            rootList.add(pkg);
                                        }
                                    }
                                    if (!dpmList.isEmpty()) {
                                        oneKeyAction(context, false, dpmList.toArray(new String[0]), dpmMode);
                                    }
                                    if (!rootList.isEmpty()) {
                                        oneKeyAction(context, false, rootList.toArray(new String[0]), API_FREEZEYOU_ROOT_DISABLE_ENABLE);
                                    }
                                }
                            } else {
                                oneKeyAction(context, false, packages, API_FREEZEYOU_ROOT_DISABLE_ENABLE);
                            }
                        }
                    } else {
                        oneKeyAction(context, freeze, packages, apiMode);
                    }
                } finally {
                    stopSelf(startId);
                }
            });
        }
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        startServiceForeground();
    }

    private void startServiceForeground() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null)
                notificationManager.createNotificationChannel(
                        new NotificationChannel(
                                "FreezeAndUnfreeze", getString(R.string.freezeAUF),
                                NotificationManager.IMPORTANCE_NONE
                        )
                );
            Notification.Builder mBuilder =
                    new Notification.Builder(this, "FreezeAndUnfreeze");
            mBuilder.setSmallIcon(R.drawable.ic_shortcut_logo);
            mBuilder.setContentText(getString(R.string.freezeAUF));
            startForeground(4, mBuilder.build());
        } else {
            startForeground(4, new Notification());
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
