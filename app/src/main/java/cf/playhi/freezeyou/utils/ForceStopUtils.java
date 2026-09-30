package cf.playhi.freezeyou.utils;

import android.content.Context;

import java.io.DataOutputStream;

import cf.playhi.freezeyou.MainApplication;
import rikka.shizuku.Shizuku;
import rikka.shizuku.ShizukuProvider;

import static cf.playhi.freezeyou.storage.key.DefaultMultiProcessMMKVStorageStringKeys.selectFUFMode;
import cf.playhi.freezeyou.R;

import static cf.playhi.freezeyou.storage.key.DefaultMultiProcessMMKVStorageBooleanKeys.lesserToast;
import static cf.playhi.freezeyou.storage.key.DefaultMultiProcessMMKVStorageBooleanKeys.avoidFreezeForegroundApplications;
import static cf.playhi.freezeyou.utils.ProcessUtils.destroyProcess;
import static cf.playhi.freezeyou.utils.ToastUtils.showToast;

public final class ForceStopUtils {

    public static void forceStop(
            Context context, String[] pkgNameList) {
        if (pkgNameList != null) {
            String currentPackage = " ";
            if (avoidFreezeForegroundApplications.getValue(null)) {
                currentPackage = MainApplication.getCurrentPackage();
            }
            if (currentPackage == null) currentPackage = " ";
            Process process = null;
            DataOutputStream outputStream = null;
            try {
                // Force stop follows the freeze modes: the elevated shell is root's su in
                // the root modes and Shizuku's own shell (root via Sui, shell via adb) in
                // the Shizuku modes. Calling su unconditionally broke Shizuku-only setups -
                // su is not on the app's PATH there, and "am force-stop" does not need it.
                int apiMode = -1;
                try {
                    String modeValue = selectFUFMode.getValue(context);
                    if (modeValue != null) apiMode = Integer.parseInt(modeValue);
                } catch (NumberFormatException ignored) {
                }
                boolean shizukuMode = apiMode >= 8 && apiMode <= 10;
                if (shizukuMode) {
                    if (!Shizuku.pingBinder()) {
                        ShizukuProvider.requestBinderForNonProviderProcess(context);
                        int waited = 0;
                        while (!Shizuku.pingBinder() && waited < 3000) {
                            Thread.sleep(50);
                            waited += 50;
                        }
                        if (!Shizuku.pingBinder()) {
                            showToast(context, R.string.mayUnrootedOrOtherEx);
                            return;
                        }
                    }
                    process = Shizuku.newProcess(new String[]{"sh"}, null, null);
                } else {
                    process = Runtime.getRuntime().exec("su");
                }
                outputStream = new DataOutputStream(process.getOutputStream());
                for (String aPkgNameList : pkgNameList) {
                    if (!context.getPackageName().equals(aPkgNameList)) {
                        if (FUFUtils.isAvoidFreezeNotifyingApplicationsEnabledAndAppStillNotifying(aPkgNameList)) {
                            FUFUtils.checkAndShowAppStillNotifyingToast(context, aPkgNameList);
                        } else if (currentPackage.equals(aPkgNameList)) {
                            FUFUtils.checkAndShowAppIsForegroundApplicationToast(context, aPkgNameList);
                        } else {
                            try {
                                outputStream.writeBytes("am force-stop " + aPkgNameList + "\n");
                            } catch (Exception e) {
                                e.printStackTrace();
//                                    if (!(new AppPreferences(context).getBoolean("lesserToast", false))) {
//                                        showToast(context, R.string.plsRemoveUninstalledApplications);
//                                    }
                            }
                        }
                    }
                }

                outputStream.writeBytes("exit\n");
                outputStream.flush();
                int exitValue = process.waitFor();
                if (exitValue == 0) {
                    if (!lesserToast.getValue(null)) {
                        showToast(context, R.string.executed);
                    }
                    FUFUtils.sendStatusChangedBroadcast(context);
                } else {
                    showToast(context, R.string.mayUnrootedOrOtherEx);
                }
                destroyProcess(outputStream, process);
            } catch (Exception e) {
                e.printStackTrace();
                showToast(context, context.getString(R.string.exception) + e.getMessage());
                if (e.getMessage().toLowerCase().contains("permission denied") || e.getMessage().toLowerCase().contains("not found")) {
                    showToast(context, R.string.mayUnrooted);
                }
                destroyProcess(outputStream, process);
            }
        }
    }

}
