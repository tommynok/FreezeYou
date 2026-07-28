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

    private static final String PS_PRIMARY_MARKER = "---PS-PRIMARY---";
    private static final String PS_FALLBACK_MARKER = "---PS-FALLBACK---";

    /**
     * @return package names of currently running processes, as seen by a root shell. Requires
     * root; returns an empty set on any failure instead of throwing, since the caller treats
     * "no matches" and "unavailable" the same way.
     * <p>
     * Reads every /proc/PID/cmdline directly as the primary source: it's what "ps" itself reads
     * under the hood, so it sidesteps quirks of whichever ps binary/toolbox happens to be on the
     * device (missing "-o" support, different column layouts, etc). The loop reads each cmdline
     * file with the shell's "read" builtin rather than piping through "tr"/"head" — those spawn
     * a process per PID, which on a device with a couple hundred processes turned "list running
     * apps" into a many-hundred-fork operation and a many-second wait. "read" runs in the su
     * shell itself, no forking. "ps -A -o NAME=" and plain "ps -A" (last whitespace-separated
     * column) are kept as supplementary sources in case some process' cmdline was unreadable but
     * ps still resolved it another way; results from all three are merged into one set.
     */
    public static Set<String> getRootRunningPackages() {
        Set<String> packages = new HashSet<>();
        Process process = null;
        DataOutputStream outputStream = null;
        try {
            process = Runtime.getRuntime().exec("su");
            outputStream = new DataOutputStream(process.getOutputStream());
            outputStream.writeBytes("for f in /proc/[0-9]*/cmdline; do read -r line < \"$f\" 2>/dev/null && [ -n \"$line\" ] && echo \"$line\"; done\n");
            outputStream.writeBytes("echo " + PS_PRIMARY_MARKER + "\n");
            outputStream.writeBytes("ps -A -o NAME= 2>/dev/null\n");
            outputStream.writeBytes("echo " + PS_FALLBACK_MARKER + "\n");
            outputStream.writeBytes("ps -A 2>/dev/null\n");
            outputStream.writeBytes("exit\n");
            outputStream.flush();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            int section = 0; // 0 = /proc, 1 = ps -o NAME=, 2 = plain ps -A
            boolean fallbackHeaderSkipped = false;
            int rawLineCount = 0;
            while ((line = reader.readLine()) != null) {
                rawLineCount++;
                line = line.trim();
                if (line.isEmpty()) continue;
                if (PS_PRIMARY_MARKER.equals(line)) {
                    section = 1;
                    continue;
                }
                if (PS_FALLBACK_MARKER.equals(line)) {
                    section = 2;
                    continue;
                }
                if (section == 2) {
                    // First non-empty line of plain "ps -A" is its column header (USER PID ... NAME).
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
