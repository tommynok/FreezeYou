package cf.playhi.freezeyou.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;

import java.io.File;

import cf.playhi.freezeyou.utils.ApplicationIconUtils;
import cf.playhi.freezeyou.utils.ApplicationInfoUtils;

import static cf.playhi.freezeyou.storage.key.DefaultSharedPreferenceStorageBooleanKeys.cacheApplicationsIcons;

public class PackageReplacedReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {

        if (intent == null) {
            return;
        }

        if (Intent.ACTION_PACKAGE_REPLACED.equals(intent.getAction())) {
            String pkgName = intent.getDataString();
            if (pkgName != null) {
                pkgName = pkgName.replace("package:", "");
                if (!ApplicationIconUtils.isValidPackageName(pkgName)) {
                    return;
                }
                ApplicationInfo applicationInfo =
                        ApplicationInfoUtils.getApplicationInfoFromPkgName(pkgName, context);
                if (applicationInfo != null) {
                    // Check settings and update application icon cache
                    if (cacheApplicationsIcons.getValue(context)) {
                        ApplicationIconUtils.deleteIconCache(context, pkgName);
                        ApplicationIconUtils
                                .getApplicationIcon(
                                        context, pkgName,
                                        applicationInfo, false, true);
                    }
                    // Update application label cache
                    context.getSharedPreferences("NameOfPackages", Context.MODE_PRIVATE)
                            .edit()
                            .putString(
                                    pkgName,
                                    context.getPackageManager()
                                            .getApplicationLabel(applicationInfo).toString())
                            .apply();
                }
            }
        }
    }
}
