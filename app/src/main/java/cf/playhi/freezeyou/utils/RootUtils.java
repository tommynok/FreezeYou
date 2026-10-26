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

    /** How long a failed discovery stands before the candidates are probed again. */
    private static final long FAILED_DISCOVERY_TTL_MS = 30_000L;

    /** When the last full probe ended without a working su binary, or 0 after a success. */
    private static volatile long sLastFailedDiscoveryTimeMs = 0L;

    /** Guards the pair above so a failing probe cannot undo a success found in parallel. */
    private static final Object CACHE_LOCK = new Object();

    /**
     * Returns a tested and functional su binary path, or "su" as fallback.
     */
    public static String getSuBinary() {
        String binary = sCachedSuBinary;
        if (binary != null) {
            return binary;
        }
        binary = findSuBinary(false);
        return binary != null ? binary : "su";
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
            resetCache();
        }
        return findSuBinary(forceRefresh) != null;
    }

    /** Forgets both the working binary and any recorded failure, so the next call probes again. */
    public static void resetCache() {
        synchronized (CACHE_LOCK) {
            sCachedSuBinary = null;
            sLastFailedDiscoveryTimeMs = 0L;
        }
    }

    /**
     * Whether a failure recorded at {@code lastFailureMs} still stands at {@code currentTimeMs}.
     * A timestamp of 0 means no failure was recorded; an elapsed time at or past the TTL, or a
     * clock that moved backwards, allows a new probe.
     */
    public static boolean isNegativeCacheActive(long currentTimeMs, long lastFailureMs,
                                                long ttlMs) {
        return lastFailureMs > 0L
                && currentTimeMs >= lastFailureMs
                && currentTimeMs - lastFailureMs < ttlMs;
    }

    private static String findSuBinary(boolean forceRefresh) {
        // A working cached binary outranks a recorded failure: a probe running in parallel may
        // have just found one, and denying it for the rest of the TTL is worse than one extra test.
        String cached = sCachedSuBinary;
        if (cached != null) {
            if (testSuBinary(cached)) {
                cacheWorkingBinary(cached);
                return cached;
            }
            // The cached path stopped answering. Forget it, so that this failure can be recorded
            // and getSuBinary stops handing out a binary that no longer works.
            invalidateCachedBinary(cached);
        }

        if (!forceRefresh
                && isNegativeCacheActive(System.currentTimeMillis(),
                sLastFailedDiscoveryTimeMs, FAILED_DISCOVERY_TTL_MS)) {
            // Every candidate was just tried and none worked. Probing them again would block the
            // caller for up to 35 s and raise the root prompt again on a device without root.
            return null;
        }

        for (String candidate : SU_CANDIDATES) {
            if (testSuBinary(candidate)) {
                cacheWorkingBinary(candidate);
                return candidate;
            }
        }
        recordFailedDiscovery();
        return null;
    }

    /** Records a working binary and clears any recorded failure, whichever thread found it. */
    private static void cacheWorkingBinary(String binary) {
        synchronized (CACHE_LOCK) {
            sCachedSuBinary = binary;
            sLastFailedDiscoveryTimeMs = 0L;
        }
    }

    /** Drops the cached binary only while it is still the one whose test just failed. */
    private static void invalidateCachedBinary(String failedBinary) {
        synchronized (CACHE_LOCK) {
            if (failedBinary.equals(sCachedSuBinary)) {
                sCachedSuBinary = null;
            }
        }
    }

    /** Records the failure unless a parallel probe cached a working binary in the meantime. */
    private static void recordFailedDiscovery() {
        synchronized (CACHE_LOCK) {
            if (sCachedSuBinary == null) {
                sLastFailedDiscoveryTimeMs = System.currentTimeMillis();
            }
        }
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
