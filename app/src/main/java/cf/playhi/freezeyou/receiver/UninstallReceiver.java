package cf.playhi.freezeyou.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import java.io.File;

import cf.playhi.freezeyou.R;
import cf.playhi.freezeyou.utils.ApplicationInfoUtils;

import static cf.playhi.freezeyou.utils.DataStatisticsUtils.deletePackageTimes;
import static cf.playhi.freezeyou.utils.NotificationUtils.deleteNotification;
import static cf.playhi.freezeyou.utils.OneKeyListUtils.removePackageFromUserDefinedCategories;
import static cf.playhi.freezeyou.utils.OneKeyListUtils.removeFromOneKeyList;

public class UninstallReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {

        if (intent == null) {
            return;
        }

        String action = intent.getAction();
        if (Intent.ACTION_PACKAGE_FULLY_REMOVED.equals(action)
                || Intent.ACTION_PACKAGE_REMOVED.equals(action)) {
            if (intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) {
                return;
            }
            String pkgName = intent.getDataString();
            if (pkgName != null) {
                pkgName = pkgName.replace("package:", "");
                if (!ApplicationInfoUtils.isAppInstalled(context, pkgName)) {
                    // Clean up notification data if not installed for current user
                    deleteNotification(context, pkgName);
                }
                if (ApplicationInfoUtils.getApplicationInfoFromPkgName(pkgName, context) == null) {
                    removeFromOneKeyList(context, context.getString(R.string.sAutoFreezeApplicationList), pkgName);
                    removeFromOneKeyList(context, context.getString(R.string.sOneKeyUFApplicationList), pkgName);
                    removeFromOneKeyList(context, context.getString(R.string.sFreezeOnceQuit), pkgName);
                    // The categories keep their own copy of the package names, so nothing above
                    // reaches them
                    removePackageFromUserDefinedCategories(context, pkgName);
                    // So does the statistics: a counter row outlived the app it counted
                    deletePackageTimes(context, pkgName);
                    // Clean up icon cache for uninstalled application
                    File file = new File(context.getFilesDir() + "/icon/" + pkgName + ".png");
                    if (file.exists() && file.isFile()) {
                        file.delete();
                    }
                    File file2 = new File(context.getCacheDir() + "/icon/" + pkgName + ".png");
                    if (file2.exists() && file2.isFile()) {
                        file2.delete();
                    }
                    // Clean up cached name for uninstalled application
                    context.getSharedPreferences("NameOfPackages", Context.MODE_PRIVATE)
                            .edit().remove(pkgName).apply();
                }
            }
        }
    }
}
