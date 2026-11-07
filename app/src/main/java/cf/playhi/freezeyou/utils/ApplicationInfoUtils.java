package cf.playhi.freezeyou.utils;

import android.content.Context;
import android.content.pm.ApplicationInfo;

import static android.content.pm.PackageManager.GET_UNINSTALLED_PACKAGES;

public final class ApplicationInfoUtils {

    public static ApplicationInfo getApplicationInfoFromPkgName(String pkgName, Context context) {
        ApplicationInfo applicationInfo = null;
        try {
            applicationInfo = context.getPackageManager().getApplicationInfo(pkgName, GET_UNINSTALLED_PACKAGES);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return applicationInfo;
    }

    /**
     * Checks if the package exists and is currently installed for the targeted user.
     * Evaluates against flags (including FLAG_INSTALLED) to avoid treating uninstalled system apps
     * or data-kept uninstalled apps as installed.
     */
    public static boolean isAppInstalled(Context context, String pkgName) {
        if (context == null || pkgName == null || pkgName.isEmpty()) return false;
        try {
            // Flags 0 throws for a package hidden through DevicePolicyManager, and the cleanup
            // callers would then delete its lists as if the package were uninstalled.
            ApplicationInfo applicationInfo =
                    context.getPackageManager().getApplicationInfo(pkgName, GET_UNINSTALLED_PACKAGES);
            return applicationInfo != null && !RestoreUtils.isAppUninstalled(applicationInfo);
        } catch (Exception e) {
            return false;
        }
    }

}
