package cf.playhi.freezeyou.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import java.util.List;

public class LocaleChangedReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {

        if (intent == null) {
            return;
        }

        if (Intent.ACTION_LOCALE_CHANGED.equals(intent.getAction())) {
            PackageManager packageManager = context.getPackageManager();
            List<ApplicationInfo> applicationInfo = packageManager.getInstalledApplications(PackageManager.GET_UNINSTALLED_PACKAGES);
            // Update cached names of all installed applications
            for (ApplicationInfo applicationInfo1 : applicationInfo) {
                context.getSharedPreferences("NameOfPackages", Context.MODE_PRIVATE)
                        .edit()
                        .putString(
                                applicationInfo1.packageName,
                                context.getPackageManager()
                                        .getApplicationLabel(applicationInfo1).toString())
                        .apply();
            }
        }
    }
}
