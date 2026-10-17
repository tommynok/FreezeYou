package cf.playhi.freezeyou.utils;

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.PowerManager;

import androidx.annotation.Nullable;

import java.io.DataOutputStream;

import cf.playhi.freezeyou.DeviceAdminReceiver;
import cf.playhi.freezeyou.R;

import static cf.playhi.freezeyou.utils.ToastUtils.showToast;

public final class DevicePolicyManagerUtils {

    public static DevicePolicyManager getDevicePolicyManager(Context context) {
        return (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
    }

    public static void openDevicePolicyManager(Context context) {
        showToast(context, R.string.needActiveAccessibilityService);
        if (context instanceof Activity) {
            ComponentName componentName = new ComponentName(context, DeviceAdminReceiver.class);
            Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
            intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, componentName);
            context.startActivity(intent);
        }
    }

    /**
     * Lock screen using ROOT if available, otherwise fall back to non-ROOT (DPM)
     *
     * @param context Context
     */
    public static void doLockScreen(Context context) {
        // Try ROOT first to avoid breaking Smart Lock
        try {
            Process process = Runtime.getRuntime().exec(RootUtils.getSuBinary());
            DataOutputStream outputStream = new DataOutputStream(process.getOutputStream());
            outputStream.writeBytes("input keyevent KEYCODE_POWER" + "\n");
            outputStream.writeBytes("exit\n");
            outputStream.flush();
            process.waitFor();
            ProcessUtils.destroyProcess(outputStream, process);
        } catch (Exception e) {
            e.printStackTrace();
        }

        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        if (pm == null || pm.isScreenOn()) {
            DevicePolicyManager devicePolicyManager = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
            ComponentName componentName = new ComponentName(context, DeviceAdminReceiver.class);
            if (devicePolicyManager != null) {
                if (devicePolicyManager.isAdminActive(componentName)) {
                    devicePolicyManager.lockNow();
                } else {
                    openDevicePolicyManager(context);
                }
            } else {
                showToast(context, R.string.devicePolicyManagerNotFound);
            }
        }
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
