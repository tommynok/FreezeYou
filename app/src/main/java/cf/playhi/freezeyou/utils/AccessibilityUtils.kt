package cf.playhi.freezeyou.utils

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.text.TextUtils
import cf.playhi.freezeyou.AccessibilityService
import cf.playhi.freezeyou.R
import cf.playhi.freezeyou.utils.ToastUtils.showToast

object AccessibilityUtils {
    @JvmStatic
    fun openAccessibilitySettings(context: Context) {
        try {
            val accessibilityIntent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            accessibilityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(accessibilityIntent)
        } catch (e: Exception) {
            e.printStackTrace()
            showToast(context, R.string.failed)
        }
    }

    //https://stackoverflow.com/questions/18094982/detect-if-my-accessibility-service-is-enabled
    @JvmStatic
    fun isAccessibilitySettingsOn(mContext: Context): Boolean {
        var accessibilityEnabled = 0
        val service = mContext.packageName + "/" + AccessibilityService::class.java.canonicalName
        try {
            accessibilityEnabled = Settings.Secure.getInt(
                mContext.applicationContext.contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED
            )
        } catch (e: Settings.SettingNotFoundException) {
            e.printStackTrace()
        }
        val mStringColonSplitter = TextUtils.SimpleStringSplitter(':')
        if (accessibilityEnabled == 1) {
            val settingValue = Settings.Secure.getString(
                mContext.applicationContext.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
            if (settingValue != null) {
                mStringColonSplitter.setString(settingValue)
                while (mStringColonSplitter.hasNext()) {
                    val accessibilityService = mStringColonSplitter.next()
                    if (accessibilityService.equals(service, ignoreCase = true)) {
                        return true
                    }
                }
            }
        } else {
            return false
        }
        return false
    }

    @JvmStatic
    fun checkAndRequestIfAccessibilitySettingsOff(context: Context) {
        if (!isAccessibilitySettingsOn(context)) {
            showToast(context, R.string.needActiveAccessibilityService)
            openAccessibilitySettings(context)
        }
    }
}
