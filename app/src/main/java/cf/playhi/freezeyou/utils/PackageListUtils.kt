package cf.playhi.freezeyou.utils

/**
 * The OneKEY lists ("OneKEY freeze", "OneKEY unfreeze") are stored as one comma separated string.
 *
 * Turning that string into package names is where the edges used to go wrong: an empty preference
 * was only guarded against being null, while the empty string was still split into a single empty
 * name - a batch of one that reported success for nothing. Names are trimmed for the same reason:
 * a stored `" a , b "` means the packages `a` and `b`, not `" a "`.
 *
 * Kept as a function over a plain string so the contract can be checked without a device.
 */
object PackageListUtils {

    /** @return the package names in [raw], trimmed, without empty entries, in the order given. */
    @JvmStatic
    fun parse(raw: String?): Array<String> {
        if (raw.isNullOrEmpty()) return emptyArray()
        return raw.split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toTypedArray()
    }
}
