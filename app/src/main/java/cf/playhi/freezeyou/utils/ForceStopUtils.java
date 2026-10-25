package cf.playhi.freezeyou.utils;

import android.content.Context;

import java.io.DataOutputStream;

import cf.playhi.freezeyou.MainApplication;
import rikka.shizuku.Shizuku;

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
                // A10A-07 / A3-05: Dynamically resolve the elevated shell backend.
                // Priority: Shizuku (if binder reachable and permission granted) -> Root (if su available) -> refuse.
                boolean shizukuMode = apiMode >= 8 && apiMode <= 10;
                boolean shizukuAvailable = PrivilegedShellUtils.isShizukuAvailable(context);
                if (shizukuMode && !shizukuAvailable) {
                    int waited = 0;
                    while (!PrivilegedShellUtils.isShizukuAvailable(context) && waited < 3000) {
                        Thread.sleep(50);
                        waited += 50;
                    }
                    shizukuAvailable = PrivilegedShellUtils.isShizukuAvailable(context);
                }

                if (shizukuAvailable) {
                    process = Shizuku.newProcess(new String[]{"sh"}, null, null);
                } else if (RootUtils.checkRootPermission()) {
                    process = Runtime.getRuntime().exec(RootUtils.getSuBinary());
                } else {
                    showToast(context, R.string.mayUnrootedOrOtherEx);
                    return;
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
                                outputStream.writeBytes("am force-stop " + PrivilegedShellUtils.shellQuote(aPkgNameList) + "\n");
                            } catch (Exception e) {
                                e.printStackTrace();
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
