package cf.playhi.freezeyou.utils;


import android.app.ActivityManager;
import android.app.Application;
import android.content.Context;
import android.os.Build;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.Set;

public final class ProcessUtils {

    public static void destroyProcess(DataOutputStream dataOutputStream, Process process1) {
        try {
            if (dataOutputStream != null) {
                dataOutputStream.close();
            }
            if (process1 != null) {
                process1.destroy();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static int fAURoot(String pkgName, Boolean enable, Boolean hideMode) throws Exception {
        Process process = Runtime.getRuntime().exec("su");
        DataOutputStream outputStream = new DataOutputStream(process.getOutputStream());
        if (enable) {
            outputStream.writeBytes("pm " + (hideMode ? "unhide " : "enable ") + pkgName + "\n");
        } else {
            outputStream.writeBytes("pm " + (hideMode ? "hide " : "disable ") + pkgName + "\n");
        }
        outputStream.writeBytes("exit\n");
        outputStream.flush();
        int i = process.waitFor();
        destroyProcess(outputStream, process);
        return i;
    }

    /**
     * @return package names of currently running processes, as seen by a root shell's
     * "ps -A -o NAME". Requires root; returns an empty set on any failure instead of throwing,
     * since the caller treats "no matches" and "unavailable" the same way.
     */
    public static Set<String> getRootRunningPackages() {
        Set<String> packages = new HashSet<>();
        Process process = null;
        DataOutputStream outputStream = null;
        try {
            process = Runtime.getRuntime().exec("su");
            outputStream = new DataOutputStream(process.getOutputStream());
            outputStream.writeBytes("ps -A -o NAME\n");
            outputStream.writeBytes("exit\n");
            outputStream.flush();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || "NAME".equals(line)) continue;
                int colonIndex = line.indexOf(':');
                packages.add(colonIndex > 0 ? line.substring(0, colonIndex) : line);
            }
            process.waitFor();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            destroyProcess(outputStream, process);
        }
        return packages;
    }

    /**
     * @param context Context
     * @return packageName:processName. If activityManager == null or pid not found, return ""
     */
    public static String getProcessName(Context context) {
        /*
         * References:
         * https://blog.csdn.net/zhe_ge_sha_shou/article/details/74333408
         * https://blog.csdn.net/weixin_35715335/article/details/117346298
         */
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return Application.getProcessName();
        } else {
            int myPid = android.os.Process.myPid();
            String processName = "";
            ActivityManager activityManager =
                    (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (activityManager != null) {
                for (ActivityManager.RunningAppProcessInfo info
                        : activityManager.getRunningAppProcesses()) {
                    if (info.pid == myPid) {
                        processName = info.processName;
                        break;
                    }
                }
            }
            return processName;
        }
    }
}
