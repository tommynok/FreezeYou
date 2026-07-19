package cf.playhi.freezeyou.utils

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import cf.playhi.freezeyou.DeviceAdminReceiver
import cf.playhi.freezeyou.R
import cf.playhi.freezeyou.utils.ToastUtils.showToast
import java.io.DataOutputStream

object DevicePolicyManagerUtils {
    @JvmStatic
    fun getDevicePolicyManager(context: Context): DevicePolicyManager {
        return context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    }

    @JvmStatic
    fun openDevicePolicyManager(context: Context) {
        showToast(context, R.string.needActiveAccessibilityService)
        if (context is Activity) {
            val componentName = ComponentName(context, DeviceAdminReceiver::class.java)
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, componentName)
            context.startActivity(intent)
        }
    }

    /**
     * 优先 ROOT 模式锁屏，失败则尝试 免ROOT 模式锁屏
     *
     * @param context Context
     */
    @JvmStatic
    fun doLockScreen(context: Context) {
        //先走ROOT，有权限的话就可以不影响SmartLock之类的了
        try {
            val process = Runtime.getRuntime().exec("su")
            val outputStream = DataOutputStream(process.outputStream)
            outputStream.writeBytes("input keyevent KEYCODE_POWER\n")
            outputStream.writeBytes("exit\n")
            outputStream.flush()
            process.waitFor()
            ProcessUtils.destroyProcess(outputStream, process)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager?
        if (pm == null || pm.isInteractive) {
            val devicePolicyManager =
                context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager?
            val componentName = ComponentName(context, DeviceAdminReceiver::class.java)
            if (devicePolicyManager != null) {
                if (devicePolicyManager.isAdminActive(componentName)) {
                    devicePolicyManager.lockNow()
                } else {
                    openDevicePolicyManager(context)
                }
            } else {
                showToast(context, R.string.devicePolicyManagerNotFound)
            }
        }
    }

    @JvmStatic
    fun isDeviceOwner(context: Context): Boolean {
        return getDevicePolicyManager(context).isDeviceOwnerApp(context.packageName)
    }

    @JvmStatic
    fun isProfileOwner(context: Context): Boolean {
        return getDevicePolicyManager(context).isProfileOwnerApp(context.packageName)
    }

    @JvmStatic
    fun checkAndSetOrganizationName(context: Context, name: String?) {
        if (Build.VERSION.SDK_INT >= 24 && (isDeviceOwner(context) || isProfileOwner(context))) {
            getDevicePolicyManager(context).setOrganizationName(
                DeviceAdminReceiver.getComponentName(context), name
            )
        }
    }
}
