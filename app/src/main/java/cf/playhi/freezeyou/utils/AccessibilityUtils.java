package cf.playhi.freezeyou.utils;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityManager;

import java.util.List;

import cf.playhi.freezeyou.AccessibilityService;
import cf.playhi.freezeyou.R;

import static cf.playhi.freezeyou.utils.ToastUtils.showToast;

public final class AccessibilityUtils {

    public static void openAccessibilitySettings(Context context) {
        try {
            Intent accessibilityIntent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            accessibilityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(accessibilityIntent);
        } catch (Exception e) {
            e.printStackTrace();
            showToast(context, R.string.failed);
        }
    }

    /**
     * Determines whether FreezeYou's Accessibility Service is currently enabled.
     *
     * <p>Checks both the system {@link AccessibilityManager} enabled services list and the
     * {@link Settings.Secure#ENABLED_ACCESSIBILITY_SERVICES} setting string, correctly parsing both
     * full ({@code pkg/pkg.Class}) and short ({@code pkg/.Class}) component name notations.
     */
    public static boolean isAccessibilitySettingsOn(Context context) {
        if (context == null) {
            return false;
        }

        ComponentName expectedComponent = new ComponentName(context, AccessibilityService.class);

        try {
            AccessibilityManager am = (AccessibilityManager) context.getSystemService(Context.ACCESSIBILITY_SERVICE);
            if (am != null) {
                List<AccessibilityServiceInfo> enabledServices =
                        am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
                if (enabledServices != null) {
                    for (AccessibilityServiceInfo enabledService : enabledServices) {
                        if (enabledService != null) {
                            ServiceInfo serviceInfo = enabledService.getResolveInfo() != null
                                    ? enabledService.getResolveInfo().serviceInfo
                                    : null;
                            if (serviceInfo != null
                                    && context.getPackageName().equals(serviceInfo.packageName)
                                    && AccessibilityService.class.getName().equals(serviceInfo.name)) {
                                return true;
                            }
                            if (enabledService.getId() != null) {
                                ComponentName enabledCn = ComponentName.unflattenFromString(enabledService.getId());
                                if (expectedComponent.equals(enabledCn)) {
                                    return true;
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            int accessibilityEnabled = Settings.Secure.getInt(
                    context.getApplicationContext().getContentResolver(),
                    Settings.Secure.ACCESSIBILITY_ENABLED);
            if (accessibilityEnabled == 1) {
                String settingValue = Settings.Secure.getString(
                        context.getApplicationContext().getContentResolver(),
                        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
                if (isComponentInSettingValue(settingValue, expectedComponent)) {
                    return true;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return false;
    }

    /**
     * Evaluates whether a target component name is included in an accessibility services colon-delimited string.
     *
     * <p>Package-private for unit testability without relying on system framework settings content resolvers.
     */
    public static boolean isComponentInSettingValue(String settingValue, ComponentName target) {
        if (settingValue == null || target == null) {
            return false;
        }
        TextUtils.SimpleStringSplitter splitter = new TextUtils.SimpleStringSplitter(':');
        splitter.setString(settingValue);
        while (splitter.hasNext()) {
            String serviceStr = splitter.next();
            if (serviceStr.equalsIgnoreCase(target.flattenToString())
                    || serviceStr.equalsIgnoreCase(target.flattenToShortString())) {
                return true;
            }
            ComponentName cn = ComponentName.unflattenFromString(serviceStr);
            if (target.equals(cn)) {
                return true;
            }
        }
        return false;
    }

    public static void checkAndRequestIfAccessibilitySettingsOff(Context context) {
        if (!AccessibilityUtils.isAccessibilitySettingsOn(context)) {
            showToast(context, R.string.needActiveAccessibilityService);
            AccessibilityUtils.openAccessibilitySettings(context);
        }
    }

}
