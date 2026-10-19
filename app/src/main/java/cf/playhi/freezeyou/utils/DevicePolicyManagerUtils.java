package cf.playhi.freezeyou.utils;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;

import androidx.annotation.Nullable;

import cf.playhi.freezeyou.DeviceAdminReceiver;
import cf.playhi.freezeyou.R;

import static cf.playhi.freezeyou.utils.ToastUtils.showToast;

public final class DevicePolicyManagerUtils {

    public static DevicePolicyManager getDevicePolicyManager(Context context) {
        return (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
    }

    public static void openDevicePolicyManager(Context context) {
        showToast(context, R.string.needActiveDeviceAdmin);
        ComponentName componentName = new ComponentName(context, DeviceAdminReceiver.class);

        // 1. Primary: Direct Device Admin activation screen
        try {
            Intent primaryIntent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
            primaryIntent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, componentName);
            primaryIntent.putExtra(
                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    context.getString(R.string.oneKeyLockScreen)
            );
            primaryIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(primaryIntent);
            return;
        } catch (Exception e) {
            e.printStackTrace();
        }

        // 2. Fallback: Device Admin settings list (supported on AOSP and most OEM ROMs)
        try {
            Intent adminListIntent = new Intent("android.settings.DEVICE_ADMIN_SETTINGS");
            adminListIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(adminListIntent);
            return;
        } catch (Exception e) {
            e.printStackTrace();
        }

        // 3. Fallback: Security settings (universal fallback across all Android versions and skins)
        try {
            Intent securityIntent = new Intent(Settings.ACTION_SECURITY_SETTINGS);
            securityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(securityIntent);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Lock screen using Shizuku or ROOT if available, otherwise fall back to non-ROOT (DPM)
     *
     * @param context Context
     * @return true if screen was locked or already off, false if user action is required
     */
    public static boolean doLockScreen(Context context) {
        // Try Shizuku first to avoid breaking Smart Lock / Biometric unlock
        if (PrivilegedShellUtils.isShizukuAvailable()) {
            try {
                PrivilegedShellUtils.CommandResult result =
                        PrivilegedShellUtils.runShizukuCommand("input keyevent 26");
                if (result.isSuccessful()) {
                    return true;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // Try ROOT next to avoid breaking Smart Lock / Biometric unlock
        if (PrivilegedShellUtils.isRootAvailable()) {
            try {
                PrivilegedShellUtils.CommandResult result =
                        PrivilegedShellUtils.runRootCommand("input keyevent 26");
                if (result.isSuccessful()) {
                    return true;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        if (pm == null || pm.isScreenOn()) {
            DevicePolicyManager devicePolicyManager = getDevicePolicyManager(context);
            ComponentName componentName = new ComponentName(context, DeviceAdminReceiver.class);
            if (devicePolicyManager != null) {
                if (devicePolicyManager.isAdminActive(componentName)) {
                    devicePolicyManager.lockNow();
                    return true;
                } else {
                    openDevicePolicyManager(context);
                    return false;
                }
            } else {
                showToast(context, R.string.devicePolicyManagerNotFound);
                return false;
            }
        }
        return true;
    }

    public static boolean isDeviceOwner(Context context) {
        return Build.VERSION.SDK_INT >= 18
                && getDevicePolicyManager(context).isDeviceOwnerApp(context.getPackageName());
    }

    public static boolean isProfileOwner(Context context) {
        return Build.VERSION.SDK_INT >= 21
                && getDevicePolicyManager(context).isProfileOwnerApp(context.getPackageName());
    }

    public static void checkAndSetOrganizationName(Context context, @Nullable String name) {
        if (Build.VERSION.SDK_INT >= 24 && (isDeviceOwner(context) || isProfileOwner(context)))
            getDevicePolicyManager(context).setOrganizationName(
                    DeviceAdminReceiver.getComponentName(context), name);
    }
}
