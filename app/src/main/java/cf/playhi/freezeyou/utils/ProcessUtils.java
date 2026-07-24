package cf.playhi.freezeyou.utils;


import android.app.ActivityManager;
import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.util.Log;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.Set;

import static cf.playhi.freezeyou.utils.DebugModeUtils.isDebugModeEnabled;

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
     * @return package names of currently running processes, as seen by a root shell. Requires
     * root; returns an empty set on any failure instead of throwing, since the caller treats
     * "no matches" and "unavailable" the same way.
     * <p>
     * "-o NAME=" (empty header name) suppresses the header line without relying on matching the
     * literal word "NAME", which some ps implementations may not use. As a fallback for ps
     * builds that don't support "-o" at all, also runs plain "ps -A" and takes the last
     * whitespace-separated column of each line (the process name in every common ps layout).
     */
    public static Set<String> getRootRunningPackages() {
        Set<String> packages = new HashSet<>();
        Process process = null;
        DataOutputStream outputStream = null;
        try {
            process = Runtime.getRuntime().exec("su");
            outputStream = new DataOutputStream(process.getOutputStream());
            outputStream.writeBytes("ps -A -o NAME= 2>/dev/null\n");
            outputStream.writeBytes("echo ---FALLBACK---\n");
            outputStream.writeBytes("ps -A 2>/dev/null\n");
            outputStream.writeBytes("exit\n");
            outputStream.flush();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            boolean inFallback = false;
            boolean fallbackHeaderSkipped = false;
            int rawLineCount = 0;
            while ((line = reader.readLine()) != null) {
                rawLineCount++;
                line = line.trim();
                if (line.isEmpty()) continue;
                if ("---FALLBACK---".equals(line)) {
                    inFallback = true;
                    continue;
                }
                if (inFallback) {
                    // First non-empty fallback line is the "ps -A" column header (USER PID ... NAME).
                    if (!fallbackHeaderSkipped) {
                        fallbackHeaderSkipped = true;
                        continue;
                    }
                    String[] columns = line.split("\\s+");
                    if (columns.length == 0) continue;
                    line = columns[columns.length - 1];
                }
                int colonIndex = line.indexOf(':');
                packages.add(colonIndex > 0 ? line.substring(0, colonIndex) : line);
            }
            process.waitFor();
            if (isDebugModeEnabled()) {
                Log.e("DebugModeLogcat", "getRootRunningPackages: rawLines=" + rawLineCount
                        + " packages=" + packages.size());
            }
        } catch (Exception e) {
            e.printStackTrace();
            if (isDebugModeEnabled()) {
                Log.e("DebugModeLogcat", "getRootRunningPackages failed: " + e);
            }
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
