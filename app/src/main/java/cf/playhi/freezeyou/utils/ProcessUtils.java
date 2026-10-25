package cf.playhi.freezeyou.utils;


import android.app.ActivityManager;
import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.util.Log;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static cf.playhi.freezeyou.utils.DebugModeUtils.isDebugModeEnabled;

public final class ProcessUtils {

    /**
     * How long the running-process scan may take. The scan is built to be quick — it reads /proc
     * with the shell's own "read" builtin instead of forking per PID — so this is not a budget for
     * the work, it is a limit on how long a shell that stopped answering may hold the thread: an
     * su approval prompt nobody taps, or a su wedged behind its own full stderr pipe. Expiring it
     * costs an empty set, which is the answer an unavailable source already gives, and the list is
     * scanned again on the next generation.
     * <p>
     * Deliberately far below {@code PrivilegedShellUtils.ROOT_APPROVAL_TIMEOUT_MS}: that one guards
     * a freeze the user asked for and has to survive a slow tap on the prompt, this one guards a
     * background list refresh that is retried on its own.
     */
    private static final long ROOT_RUNNING_PACKAGES_TIMEOUT_MS = 10_000L;

    /**
     * Where the shell's streams are read and written, so that a shell which stops answering can be
     * abandoned rather than waited on — a thread blocked in {@code read()} cannot time itself out.
     * Daemon threads, because a read that never returns must not be able to hold the process open.
     */
    private static final ExecutorService SHELL_IO_EXECUTOR =
            Executors.newCachedThreadPool(r -> {
                Thread t = new Thread(r, "process-utils-shell-io");
                t.setDaemon(true);
                return t;
            });

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
        Process process = Runtime.getRuntime().exec(RootUtils.getSuBinary());
        DataOutputStream outputStream = new DataOutputStream(process.getOutputStream());
        String quotedPkg = PrivilegedShellUtils.shellQuote(pkgName);
        if (enable) {
            outputStream.writeBytes("pm " + (hideMode ? "unhide " : "enable ") + quotedPkg + "\n");
        } else {
            outputStream.writeBytes("pm " + (hideMode ? "hide " : "disable ") + quotedPkg + "\n");
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
     * The /proc/cmdline scan plus the two ps fallbacks, one command stream. Run it through a
     * root shell (Runtime.exec("su")) or through Shizuku's shell (Shizuku.newProcess) — both
     * carry the privileges /proc needs, and neither touches a hidden API.
     */
    public static final String[] RUNNING_PACKAGES_COMMANDS = {
            "for f in /proc/[0-9]*/cmdline; do read -r line < \"$f\" 2>/dev/null && [ -n \"$line\" ] && echo \"$line\"; done",
            "echo " + PS_PRIMARY_MARKER,
            "ps -A -o NAME= 2>/dev/null",
            "echo " + PS_FALLBACK_MARKER,
            "ps -A 2>/dev/null",
    };

    /**
     * Parses the output of {@link #RUNNING_PACKAGES_COMMANDS}: package names of currently
     * running processes. The /proc section is the primary source — it's what "ps" itself reads
     * under the hood, so it sidesteps quirks of whichever ps binary/toolbox happens to be on the
     * device (missing "-o" support, different column layouts, etc). The loop reads each cmdline
     * file with the shell's "read" builtin rather than piping through "tr"/"head" — those spawn
     * a process per PID, which on a device with a couple hundred processes turned "list running
     * apps" into a many-hundred-fork operation and a many-second wait. "read" runs in the
     * elevated shell itself, no forking. "ps -A -o NAME=" and plain "ps -A" (last
     * whitespace-separated column) are kept as supplementary sources in case some process'
     * cmdline was unreadable but ps still resolved it another way; results from all three are
     * merged into one set. Returns an empty set on any parse failure instead of throwing, since
     * the caller treats "no matches" and "unavailable" the same way.
     */
    public static Set<String> parseRunningPackages(InputStream inputStream) throws IOException {
        Set<String> packages = new HashSet<>();
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        String line;
        int section = 0; // 0 = /proc, 1 = ps -o NAME=, 2 = plain ps -A
        boolean fallbackHeaderSkipped = false;
        while ((line = reader.readLine()) != null) {
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
        return packages;
    }

    /**
     * The same scan under a deadline. It moves the read to {@link #SHELL_IO_EXECUTOR} because a
     * thread blocked in {@code read()} cannot limit itself, and the shell on the other end is not
     * obliged to ever produce another byte.
     *
     * @throws TimeoutException if the scan did not finish in time. The caller is then expected to
     *                          destroy the process, which closes its streams and is what actually
     *                          releases the worker still sitting on the read.
     */
    public static Set<String> parseRunningPackages(InputStream inputStream, long timeoutMs)
            throws IOException, InterruptedException, ExecutionException, TimeoutException {
        Future<Set<String>> reading =
                SHELL_IO_EXECUTOR.submit(() -> parseRunningPackages(inputStream));
        try {
            return reading.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            reading.cancel(true);
            throw e;
        } catch (InterruptedException e) {
            reading.cancel(true);
            Thread.currentThread().interrupt();
            throw e;
        }
    }

    public static Set<String> getRootRunningPackages() {
        Set<String> packages = new HashSet<>();
        Process process = null;
        DataOutputStream outputStream = null;
        Future<?> errorStream = null;
        try {
            process = Runtime.getRuntime().exec(RootUtils.getSuBinary());
            outputStream = new DataOutputStream(process.getOutputStream());
            for (String command : RUNNING_PACKAGES_COMMANDS) {
                outputStream.writeBytes(command + "\n");
            }
            outputStream.writeBytes("exit\n");
            outputStream.flush();

            // Nothing wants what the shell says on stderr, but it has to be taken away: su and the
            // utilities it runs write there, and once the OS pipe buffer is full their next write
            // blocks. A blocked shell produces no stdout either, so the scan would sit waiting on a
            // process that is waiting on us — a deadlock the stdout deadline alone cannot explain.
            final Process session = process;
            errorStream = SHELL_IO_EXECUTOR.submit(() -> {
                discard(session.getErrorStream());
                return null;
            });

            packages = parseRunningPackages(
                    process.getInputStream(), ROOT_RUNNING_PACKAGES_TIMEOUT_MS);
            if (isDebugModeEnabled()) {
                Log.e("DebugModeLogcat", "getRootRunningPackages: packages=" + packages.size());
            }
        } catch (TimeoutException e) {
            if (isDebugModeEnabled()) {
                Log.e("DebugModeLogcat", "getRootRunningPackages gave up after "
                        + ROOT_RUNNING_PACKAGES_TIMEOUT_MS + "ms");
            }
        } catch (Exception e) {
            e.printStackTrace();
            if (isDebugModeEnabled()) {
                Log.e("DebugModeLogcat", "getRootRunningPackages failed: " + e);
            }
        } finally {
            if (errorStream != null) {
                errorStream.cancel(true);
            }
            destroyProcess(outputStream, process);
        }
        return packages;
    }

    /**
     * Empties a stream whose content nobody wants, so that whoever is writing to it can go on.
     */
    private static void discard(InputStream stream) {
        if (stream == null) return;
        byte[] buffer = new byte[4096];
        try {
            while (stream.read(buffer) != -1) {
                // read for the draining, not for the bytes
            }
        } catch (IOException ignored) {
            // Destroying the process closes this stream from under the reader, and that is the
            // normal end of a drain rather than a failure of it.
        }
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
