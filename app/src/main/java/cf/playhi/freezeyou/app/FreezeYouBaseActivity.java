package cf.playhi.freezeyou.app;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.WindowManager;

import java.util.List;

import androidx.annotation.CallSuper;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.tencent.mmkv.MMKV;

import java.util.Date;
import java.util.Locale;

import cf.playhi.freezeyou.storage.key.DefaultMultiProcessMMKVStorageStringKeys;
import cf.playhi.freezeyou.ui.AppLockActivity;
import cf.playhi.freezeyou.utils.ProcessUtils;

import static cf.playhi.freezeyou.storage.key.DefaultMultiProcessMMKVStorageBooleanKeys.lockOnScreenOff;
import static cf.playhi.freezeyou.storage.key.DefaultMultiProcessMMKVStorageBooleanKeys.showInRecents;
import static cf.playhi.freezeyou.storage.key.DefaultMultiProcessMMKVStorageStringKeys.appLockTimeout;
import static cf.playhi.freezeyou.utils.AuthenticationUtils.isAuthenticationEnabled;
import static cf.playhi.freezeyou.utils.AuthenticationUtils.isAuthenticatorAvailable;
import static cf.playhi.freezeyou.utils.AuthenticationUtils.isLockOnEntryEnabled;
import static cf.playhi.freezeyou.utils.Support.checkLanguage;
import static cf.playhi.freezeyou.utils.Support.getLocalString;

public class FreezeYouBaseActivity extends AppCompatActivity {
    private static final int APP_LOCK_ACTIVITY_REQUEST_CODE = 65533;

    // The lock state lives in the ashmem store because the settings screen writes it in one
    // process and an activity of another process reads it.
    private static final String ASHMEM_KV_ID = "AshmemKV";
    private static final String UNLOCK_TIME_KEY = "unlockTime";
    private static final String BACKGROUND_TIME_KEY = "backgroundTime";
    private static final String SCREEN_OFF_LOCK_KEY = "screenOffLock";

    private static final long TIMEOUT_IMMEDIATELY = 0L;
    private static final long TIMEOUT_DEFAULT_MS = 900000L;

    private static volatile boolean sProcessUnlocked = false;

    private String mUnlockLogoPkgName;
    private boolean mHadBeenUnlocked = false;

    @Override
    @CallSuper
    protected void attachBaseContext(Context newBase) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            String locale = getLocalString(newBase);
            Configuration configuration = new Configuration();
            configuration.setLocale(
                    DefaultMultiProcessMMKVStorageStringKeys.languagePref.defaultValue().equalsIgnoreCase(locale)
                            ? Locale.getDefault() : Locale.forLanguageTag(locale)
            );
            Context context = newBase.createConfigurationContext(configuration);
            super.attachBaseContext(context);
        } else {
            super.attachBaseContext(newBase);
        }
    }

    @Override
    @CallSuper
    protected void onCreate(Bundle savedInstanceState) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            checkLanguage(this);
        }
        super.onCreate(savedInstanceState);
        applyRecentsVisibility();
        reapplyLocalizedTitle();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // The app already picks its own light/dark theme manually (see ThemeUtils).
            // Some OEM skins (MIUI, EMUI, One UI, ...) additionally apply their own
            // system-wide dark-mode color inversion on top of that, which can leave
            // individual views (e.g. preference dialog text/icons) unreadable
            // against the app's own dark background. Opt this window out of it.
            getWindow().getDecorView().setForceDarkAllowed(false);
        }
    }

    /**
     * attachBaseContext re-creates this activity's resources in the language picked in the app,
     * but the title the decor toolbar initially shows comes from the manifest label, which the
     * package manager resolves with the *system* configuration — so with system Russian and
     * in-app English every sub-screen's title stayed Russian while its content was English.
     * Re-resolving the same label resource through the localized resources fixes it for every
     * activity extending this class, without touching per-activity code.
     */
    private void reapplyLocalizedTitle() {
        try {
            int labelRes =
                    getPackageManager()
                            .getActivityInfo(getComponentName(), 0)
                            .labelRes;
            if (labelRes != 0) {
                setTitle(labelRes);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    protected boolean checkAppLock() {
        if (activityNeedCheckAppLock() && screenLockEnabled()
                && isAuthenticatorAvailable(this)) {
            if (isLocked()) {
                mHadBeenUnlocked = false;
                startActivityForResult(
                        new Intent(this, AppLockActivity.class)
                                .putExtra("unlockLogoPkgName", mUnlockLogoPkgName),
                        APP_LOCK_ACTIVITY_REQUEST_CODE
                );
                if (Build.VERSION.SDK_INT >= 34) {
                    overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0);
                } else {
                    overridePendingTransition(0, 0);
                }
                return true;
            } else {
                mHadBeenUnlocked = true;
            }
        }
        return false;
    }

    @Override
    @CallSuper
    protected void onResume() {
        super.onResume();
        applyRecentsVisibility();
        checkAppLock();
    }

    public void applyRecentsVisibility() {
        boolean show = showInRecents.getValue(null);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
                if (am != null) {
                    List<ActivityManager.AppTask> tasks = am.getAppTasks();
                    if (tasks != null) {
                        int taskId = getTaskId();
                        for (ActivityManager.AppTask task : tasks) {
                            ActivityManager.RecentTaskInfo info = task.getTaskInfo();
                            if (info != null && (info.taskId == taskId || info.id == taskId || tasks.size() == 1)) {
                                task.setExcludeFromRecents(!show);
                            }
                        }
                    }
                }
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
        if (!show) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
        }
    }

    @Override
    @CallSuper
    protected void onPause() {
        super.onPause();
        if (mHadBeenUnlocked) {
            resetLockWaitTime(getLockStore(this));
        }
    }

    @Override
    @CallSuper
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == APP_LOCK_ACTIVITY_REQUEST_CODE && resultCode == RESULT_CANCELED) {
            finish();
        }
    }

    @Override
    @CallSuper
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean dispatchTouchEvent(android.view.MotionEvent ev) {
        try {
            return super.dispatchTouchEvent(ev);
        } catch (Throwable t) {
            // Guard against broken third-party Xposed modules or accessibility
            // hooks (e.g. TextXposed) that throw exceptions during touch event dispatch.
            t.printStackTrace();
            return false;
        }
    }

    /**
     * If the activity does not need the app lock logic,
     * override the method and return false.
     *
     * @return Whether the app lock needs to be checked.
     */
    protected boolean activityNeedCheckAppLock() {
        return true;
    }

    /**
     * Whether this screen is one the lock guards. By default it is guarded while authentication is
     * on and the entry has not been opted out. The shortcuts and the lock screen override this to
     * answer to authentication alone: turning the entry question off must not open the shortcuts or
     * the applications on the App Lock list with it.
     */
    protected boolean screenLockEnabled() {
        return isAuthenticationEnabled() && isLockOnEntryEnabled();
    }

    protected boolean isLocked() {
        if (!screenLockEnabled()) return false;
        if (!isAuthenticatorAvailable(this)) return false;

        MMKV mmkv = getLockStore(this);
        long unlockTime = mmkv.decodeLong(UNLOCK_TIME_KEY, 0);
        // An install that has never been unlocked has no timestamp to age; the lock screen comes
        // first whatever the timeout says.
        if (unlockTime == 0) return true;

        long timeoutMs = readAppLockTimeoutMs();

        if (lockOnScreenOff.getValue(null)) {
            if (!sProcessUnlocked || mmkv.decodeBool(SCREEN_OFF_LOCK_KEY, false)) {
                return true;
            }
        }
        if (timeoutMs == TIMEOUT_IMMEDIATELY) {
            return mmkv.decodeLong(BACKGROUND_TIME_KEY, 0) > 0;
        }

        long currentTime = new Date().getTime();
        // Whichever came later, the last refresh of the wait time or the app leaving the
        // foreground: an activity switch inside the app moves neither of them far enough to lock.
        if (currentTime - Math.max(unlockTime, mmkv.decodeLong(BACKGROUND_TIME_KEY, 0)) >= timeoutMs) {
            return true;
        }
        resetLockWaitTime(mmkv);
        return false;
    }

    protected boolean resetLockWaitTime(MMKV mmkv) {
        return mmkv.encode(UNLOCK_TIME_KEY, new Date().getTime());
    }

    /** The store the lock state is shared through. */
    public static MMKV getLockStore(Context context) {
        return MMKV.mmkvWithAshmemID(
                context.getApplicationContext(), ASHMEM_KV_ID,
                32, MMKV.MULTI_PROCESS_MODE, null
        );
    }

    public static void resetProcessStartedCount(Context context) {
        getLockStore(context).encode("started_" + ProcessUtils.getProcessName(context), 0);
    }

    public static void onActivityStarted(Context context) {
        String processKey = "started_" + ProcessUtils.getProcessName(context);
        MMKV mmkv = getLockStore(context);
        int count = mmkv.decodeInt(processKey, 0) + 1;
        mmkv.encode(processKey, count);
    }

    public static void onActivityStopped(Context context, boolean isChangingConfigurations) {
        String processKey = "started_" + ProcessUtils.getProcessName(context);
        MMKV mmkv = getLockStore(context);
        int count = Math.max(0, mmkv.decodeInt(processKey, 0) - 1);
        mmkv.encode(processKey, count);

        if (!isChangingConfigurations && !hasVisibleActivities(context, mmkv)) {
            mmkv.encode(BACKGROUND_TIME_KEY, new Date().getTime());
        }
    }

    private static boolean isProcessAlive(Context context, String targetProcessName) {
        try {
            ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null) {
                java.util.List<ActivityManager.RunningAppProcessInfo> procs = am.getRunningAppProcesses();
                if (procs != null) {
                    for (ActivityManager.RunningAppProcessInfo info : procs) {
                        if (targetProcessName.equals(info.processName)) {
                            return true;
                        }
                    }
                }
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
        return false;
    }

    private static boolean hasVisibleActivities(Context context, MMKV mmkv) {
        String pkg = context.getPackageName();
        if (mmkv.decodeInt("started_" + pkg, 0) > 0) {
            return true;
        }
        String installProcess = pkg + ":installAndUninstall";
        if (mmkv.decodeInt("started_" + installProcess, 0) > 0) {
            if (isProcessAlive(context, installProcess)) {
                return true;
            } else {
                mmkv.encode("started_" + installProcess, 0);
            }
        }
        return false;
    }

    /**
     * Called when the last started activity of the process stops. Only a real exit reaches this
     * point: on a switch between two screens of the app the next activity starts before the
     * previous one stops, so the count never falls to zero. A configuration change is skipped for
     * the same reason — the rotation restarts the screen, it does not leave it.
     */
    public static void markAppMovedToBackground(Context context) {
        getLockStore(context).encode(BACKGROUND_TIME_KEY, new Date().getTime());
    }

    /** Called by the screen-off receiver; the lock is raised even before the timeout expires. */
    public static void markLockedByScreenOff(Context context) {
        sProcessUnlocked = false;
        getLockStore(context).encode(SCREEN_OFF_LOCK_KEY, true);
    }

    /** Called on a successful unlock: the wait time starts over and both lock triggers clear. */
    public static void resetLockState(Context context) {
        sProcessUnlocked = true;
        MMKV mmkv = getLockStore(context);
        mmkv.encode(UNLOCK_TIME_KEY, new Date().getTime());
        mmkv.removeValueForKey(BACKGROUND_TIME_KEY);
        mmkv.removeValueForKey(SCREEN_OFF_LOCK_KEY);
    }

    private static long readAppLockTimeoutMs() {
        try {
            long timeout = Long.parseLong(appLockTimeout.getValue(null).trim());
            // A stored "Never" (-1) left the lock asking nothing with the screen-off trigger off;
            // the option is gone, and the stored value reads as the default.
            return timeout == -1L ? TIMEOUT_DEFAULT_MS : timeout;
        } catch (Exception e) {
            // A store that holds no readable timeout keeps the one the lock had hardcoded before
            // it became a setting, rather than an unlocked app or a permanent lock screen.
            return TIMEOUT_DEFAULT_MS;
        }
    }

    protected void setUnlockLogoPkgName(String pkgName) {
        mUnlockLogoPkgName = pkgName;
    }

}
