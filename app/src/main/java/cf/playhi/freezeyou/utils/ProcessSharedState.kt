package cf.playhi.freezeyou.utils

import cf.playhi.freezeyou.storage.mmkv.DefaultMultiProcessMMKVStorage

/**
 * Runtime facts that one process observes and another one needs.
 *
 * Both watchers live in `:backgroundService` — the accessibility service, which knows which
 * application is in front, and the notification listener, which knows which ones are showing a
 * notification. Both used to keep what they saw in a plain static field, which only the process
 * holding it can read. That was enough while freezing also happened in that process; once a
 * single freeze moved to the main process, the two "do not freeze this" guards were reading
 * fields that are empty there and never stopped anything.
 *
 * The multi-process MMKV the settings already use crosses that boundary, and reads are a memory
 * mapping rather than IPC, so a guard consulted before every freeze stays cheap.
 */
object ProcessSharedState {

    private const val KEY_FOREGROUND_PACKAGE = "runtimeForegroundPackage"
    private const val KEY_NOTIFYING_PACKAGES = "runtimeNotifyingPackages"

    /** Package names cannot contain a newline, so it can separate them safely. */
    private const val SEPARATOR = "\n"

    @JvmStatic
    fun setForegroundPackage(pkgName: String?) {
        if (pkgName.isNullOrEmpty()) return
        DefaultMultiProcessMMKVStorage().putString(KEY_FOREGROUND_PACKAGE, pkgName)
    }

    @JvmStatic
    fun getForegroundPackage(): String? {
        return DefaultMultiProcessMMKVStorage().getString(KEY_FOREGROUND_PACKAGE, null)
    }

    /**
     * @param pkgNames every application currently showing a notification; an empty set when the
     * listener is disconnected, so a stale list cannot keep blocking freezes.
     */
    @JvmStatic
    fun setNotifyingPackages(pkgNames: Collection<String>) {
        DefaultMultiProcessMMKVStorage()
            .putString(KEY_NOTIFYING_PACKAGES, pkgNames.toSet().joinToString(SEPARATOR))
    }

    @JvmStatic
    fun isNotifying(pkgName: String?): Boolean {
        if (pkgName.isNullOrEmpty()) return false
        val stored = DefaultMultiProcessMMKVStorage()
            .getString(KEY_NOTIFYING_PACKAGES, null) ?: return false
        return stored.splitToSequence(SEPARATOR).any { it == pkgName }
    }
}
