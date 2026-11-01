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
    private const val KEY_UNLOCKED_PACKAGES = "runtimeUnlockedPackages"

    /** Package names cannot contain a newline, so it can separate them safely. */
    private const val SEPARATOR = "\n"

    @JvmStatic
    fun setForegroundPackage(pkgName: String?) {
        if (pkgName.isNullOrEmpty()) return
        if (pkgName == getForegroundPackage()) return
        DefaultMultiProcessMMKVStorage().putString(KEY_FOREGROUND_PACKAGE, pkgName)
    }

    /**
     * Called when the accessibility service is unbound: what it last saw is no longer news, and a
     * value nobody updates any more would keep a guard refusing freezes forever.
     */
    @JvmStatic
    fun clearForegroundPackage() {
        DefaultMultiProcessMMKVStorage().putString(KEY_FOREGROUND_PACKAGE, "")
    }

    @JvmStatic
    fun getForegroundPackage(): String? {
        return DefaultMultiProcessMMKVStorage()
            .getString(KEY_FOREGROUND_PACKAGE, null)
            ?.takeIf { it.isNotEmpty() }
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

    /**
     * The applications the user has authenticated for and has not left yet.
     *
     * The lock screen is raised by `:backgroundService`, which is the only thing that sees the
     * window change, and the authentication succeeds inside an activity of the main process, so
     * "this one is open now" has to cross the same boundary the foreground package does. The set is
     * a comma list like the OneKEY lists and is edited through the same helpers rather than by
     * concatenation: a shorter name must not cut a piece out of a longer one on the way out.
     *
     * An entry lives as long as the application is the one in front of the user, and the store
     * outlives the process, so whoever stops watching clears it all - see [clearAllUnlockedPackages].
     */
    @JvmStatic
    fun setPackageUnlocked(pkgName: String?) {
        val current = currentUnlockedPackages()
        writeUnlockedPackages(current, unlockedPackagesAfterUnlock(current, pkgName))
    }

    @JvmStatic
    fun isPackageUnlocked(pkgName: String?): Boolean =
        isUnlockedIn(currentUnlockedPackages(), pkgName)

    /** The session unlock was for as long as this application was the one in front. */
    @JvmStatic
    fun clearPackageUnlocked(pkgName: String?) {
        val current = currentUnlockedPackages()
        writeUnlockedPackages(current, unlockedPackagesAfterLock(current, pkgName))
    }

    /**
     * Everything the user was let into is locked again: a service that restarted, or a device that
     * rebooted, must not find the applications of the last session still open in front of whoever
     * picks the phone up.
     */
    @JvmStatic
    fun clearAllUnlockedPackages() {
        writeUnlockedPackages(currentUnlockedPackages(), "")
    }

    /**
     * Whether [pkgName] is open in the session the raw set describes.
     *
     * Kept as a function over the stored string, like the OneKEY list helpers it is built from, so
     * the shape of the set can be read and tested without the store behind it.
     */
    @JvmStatic
    fun isUnlockedIn(raw: String?, pkgName: String?): Boolean {
        if (pkgName.isNullOrEmpty()) return false
        return OneKeyListUtils.existsInOneKeyList(raw, pkgName)
    }

    /** @return the session set with [pkgName] open in it; already in it, it is returned unchanged. */
    @JvmStatic
    fun unlockedPackagesAfterUnlock(raw: String?, pkgName: String?): String {
        if (pkgName.isNullOrEmpty()) return raw ?: ""
        return OneKeyListUtils.addPkgToPkgNames(raw, pkgName)
    }

    /** @return the session set with [pkgName] locked again; not in it, it is returned unchanged. */
    @JvmStatic
    fun unlockedPackagesAfterLock(raw: String?, pkgName: String?): String {
        if (pkgName.isNullOrEmpty()) return raw ?: ""
        return OneKeyListUtils.removePkgFromPkgNames(raw, pkgName)
    }

    /** One write per real change: a window change that locks nothing is not a trip to the store. */
    private fun writeUnlockedPackages(current: String, updated: String) {
        if (current == updated) return
        DefaultMultiProcessMMKVStorage().putString(KEY_UNLOCKED_PACKAGES, updated).sync()
    }

    private fun currentUnlockedPackages(): String =
        DefaultMultiProcessMMKVStorage().getString(KEY_UNLOCKED_PACKAGES, "") ?: ""
}
