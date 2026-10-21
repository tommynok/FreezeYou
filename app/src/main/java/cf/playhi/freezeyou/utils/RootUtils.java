package cf.playhi.freezeyou.utils;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public final class RootUtils {

    private RootUtils() {}

    private static final String[] SU_CANDIDATES = {
            "su",
            "/system/bin/su",
            "/system/xbin/su",
            "/su/bin/su",
            "/data/adb/ksu/bin/su",
            "/data/adb/ap/bin/su",
            "/sbin/su"
    };

    private static volatile String sCachedSuBinary = null;

    /**
     * Returns a tested and functional su binary path, or "su" as fallback.
     */
    public static String getSuBinary() {
        String binary = sCachedSuBinary;
        if (binary != null) {
            return binary;
        }
        binary = findSuBinary();
        if (binary != null) {
            sCachedSuBinary = binary;
            return binary;
        }
        return "su";
    }

    /**
     * Checks whether Root permission is already tested and cached, avoiding synchronous su probes.
     */
    public static boolean isRootAvailableFast() {
        return sCachedSuBinary != null;
    }

    /**
     * Checks whether Root permission is available and functional.
     */
    public static boolean checkRootPermission() {
        return checkRootPermission(false);
    }

    /**
     * Checks whether Root permission is available and functional,
     * optionally forcing a probe of all candidates.
     */
    public static boolean checkRootPermission(boolean forceRefresh) {
        if (forceRefresh) {
            sCachedSuBinary = null;
        }
        String binary = findSuBinary();
        if (binary != null) {
            sCachedSuBinary = binary;
            return true;
        }
        return false;
    }

    private static String findSuBinary() {
        String cached = sCachedSuBinary;
        if (cached != null && testSuBinary(cached)) {
            return cached;
        }

        for (String candidate : SU_CANDIDATES) {
            if (testSuBinary(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static boolean testSuBinary(final String candidate) {
        final Process[] procHolder = new Process[1];
        final boolean[] resultHolder = new boolean[1];
        Thread worker = new Thread(new Runnable() {
            @Override
            public void run() {
                Process process = null;
                try {
                    process = Runtime.getRuntime().exec(new String[]{candidate, "-c", "id 2>&1"});
                    procHolder[0] = process;
                    try {
                        process.getOutputStream().close();
                    } catch (Throwable ignored) {
                    }
                    StringBuilder sb = new StringBuilder();
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(process.getInputStream()))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            sb.append(line).append('\n');
                        }
                    }
                    int exitCode = process.waitFor();
                    if (exitCode == 0 && sb.toString().contains("uid=0")) {
                        resultHolder[0] = true;
                    }
                } catch (Throwable ignored) {
                } finally {
                    if (process != null) {
                        try {
                            process.destroy();
                        } catch (Throwable ignored) {
                        }
                    }
                }
            }
        });
        worker.setName("RootUtils-testSuBinary");
        worker.start();
        try {
            worker.join(5000);
        } catch (InterruptedException e) {
            worker.interrupt();
        }
        if (worker.isAlive()) {
            worker.interrupt();
            if (procHolder[0] != null) {
                try {
                    procHolder[0].destroy();
                } catch (Throwable ignored) {
                }
            }
            return false;
        }
        return resultHolder[0];
    }
}
