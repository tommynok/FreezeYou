package cf.playhi.freezeyou.utils;

/**
 * A short-lived snapshot of the running-apps scan, kept so the first opening of a running tab can
 * reuse a background preload instead of paying for the privileged /proc scan and the top/meminfo
 * sampling again. The payload is generic so the freshness rule can be exercised on a plain JVM
 * without the Android-dependent scan types it carries in the app.
 * <p>
 * Every method is synchronized: the preload writes from its own worker while a list generation
 * reads from the AppListRefresh worker, and the two must not see a half-written snapshot.
 */
public final class RunningAppsUsageCache<T> {

    private T payload;
    private long builtAtMs;
    private boolean hasPayload;

    public synchronized void store(T payload, long nowMs) {
        if (payload == null) {
            return;
        }
        this.payload = payload;
        this.builtAtMs = nowMs;
        this.hasPayload = true;
    }

    /**
     * Returns the stored payload only while it is younger than {@code maxAgeMs}. A clock that moved
     * backwards ({@code nowMs < builtAtMs}) counts as stale rather than as a negative age that would
     * otherwise read fresh forever, and an empty cache returns null. The age boundary is inclusive:
     * a snapshot exactly {@code maxAgeMs} old is still used.
     */
    public synchronized T freshPayload(long nowMs, long maxAgeMs) {
        if (!hasPayload || payload == null) {
            return null;
        }
        if (nowMs < builtAtMs) {
            return null;
        }
        if (nowMs - builtAtMs > maxAgeMs) {
            return null;
        }
        return payload;
    }

    public synchronized void clear() {
        payload = null;
        hasPayload = false;
        builtAtMs = 0;
    }
}