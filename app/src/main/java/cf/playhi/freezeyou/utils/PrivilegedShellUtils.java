package cf.playhi.freezeyou.utils;

import android.content.pm.PackageManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Locale;

/** Runs one privileged shell command and retains its real result for the caller. */
public final class PrivilegedShellUtils {

    private PrivilegedShellUtils() {}

    public static final class CommandResult {
        public final int exitCode;
        public final String output;

        private CommandResult(int exitCode, String output) {
            this.exitCode = exitCode;
            this.output = output;
        }

        public boolean isSuccessful() {
            return PrivilegedShellUtils.isSuccessful(exitCode, output);
        }
    }

    public static boolean isShizukuAvailable() {
        try {
            return rikka.shizuku.Shizuku.pingBinder()
                    && rikka.shizuku.Shizuku.checkSelfPermission()
                    == PackageManager.PERMISSION_GRANTED;
        } catch (Exception ignored) {
            return false;
        }
    }

    public static CommandResult runShizukuCommand(String command) throws Exception {
        Process process = rikka.shizuku.Shizuku.newProcess(
                new String[]{"sh", "-c", redirectOutput(command)}, null, null);
        return collectResult(process);
    }

    public static CommandResult runRootCommand(String command)
            throws IOException, InterruptedException {
        Process process = Runtime.getRuntime().exec(
                new String[]{"su", "-c", redirectOutput(command)});
        return collectResult(process);
    }

    public static String shellQuote(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Shell argument must not be null");
        }
        return "'" + value.replace("'", "'\\''") + "'";
    }

    public static boolean isSuccessful(int exitCode, String output) {
        if (exitCode != 0) return false;
        if (output == null || output.isEmpty()) return true;

        for (String line : output.split("\\r?\\n")) {
            String normalized = line.trim().toLowerCase(Locale.ROOT);
            if (normalized.startsWith("failure")
                    || normalized.startsWith("error")
                    || normalized.startsWith("exception")
                    || normalized.startsWith("failed")
                    || normalized.startsWith("unknown command")) {
                return false;
            }
        }
        return true;
    }

    private static String redirectOutput(String command) {
        return "(" + command + ") 2>&1";
    }

    private static CommandResult collectResult(Process process)
            throws IOException, InterruptedException {
        StringBuilder output = new StringBuilder();
        try {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (output.length() > 0) output.append('\n');
                    output.append(line);
                }
            }
            return new CommandResult(process.waitFor(), output.toString());
        } finally {
            process.destroy();
        }
    }
}
