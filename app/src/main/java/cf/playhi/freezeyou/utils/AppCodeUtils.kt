package cf.playhi.freezeyou.utils

import android.util.Base64
import cf.playhi.freezeyou.storage.mmkv.DefaultMultiProcessMMKVStorage
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * The code built into FreezeYou, an alternative to the phone's own lock for the screens this
 * application guards. Only a salted hash is kept, never the code; the rounds make guessing slow.
 *
 * The hash and the attempt counters live under literal keys rather than the settings enums, so a
 * backup never carries the secret out of the device. The counters are persisted: killing the
 * process must not hand a fresh set of attempts to whoever is guessing.
 */
object AppCodeUtils {

    const val MIN_CODE_LENGTH = 4

    private const val MAX_ATTEMPTS = 5L
    private const val COOLDOWN_MS = 15000L
    private const val ROUNDS = 50000
    private const val SALT_BYTES = 16

    private const val KEY_HASH = "appCodeHash"
    private const val KEY_WRONG_ATTEMPTS = "appCodeWrongAttempts"
    private const val KEY_LOCKED_UNTIL = "appCodeLockedUntil"

    @JvmStatic
    fun hasCode(): Boolean {
        val stored = DefaultMultiProcessMMKVStorage().getString(KEY_HASH, null)
        return !stored.isNullOrEmpty() && stored.contains(':')
    }

    @JvmStatic
    fun setCode(code: String) {
        val salt = ByteArray(SALT_BYTES)
        SecureRandom().nextBytes(salt)
        val saltText = Base64.encodeToString(salt, Base64.NO_WRAP)
        val storage = DefaultMultiProcessMMKVStorage()
        storage.putString(KEY_HASH, saltText + ":" + hash(salt, code))
        storage.putLong(KEY_WRONG_ATTEMPTS, 0)
        storage.putLong(KEY_LOCKED_UNTIL, 0)
        storage.sync()
    }

    @JvmStatic
    fun clearCode() {
        val storage = DefaultMultiProcessMMKVStorage()
        storage.putString(KEY_HASH, "")
        storage.putLong(KEY_WRONG_ATTEMPTS, 0)
        storage.putLong(KEY_LOCKED_UNTIL, 0)
        storage.sync()
    }

    /** Constant-time comparison of a candidate against the stored salt:hash. */
    @JvmStatic
    fun verifyCode(code: String): Boolean {
        val stored = DefaultMultiProcessMMKVStorage().getString(KEY_HASH, null) ?: return false
        val at = stored.indexOf(':')
        if (at < 0) return false
        return try {
            val salt = Base64.decode(stored.substring(0, at), Base64.NO_WRAP)
            val expected = stored.substring(at + 1)
            MessageDigest.isEqual(
                hash(salt, code).toByteArray(Charsets.UTF_8),
                expected.toByteArray(Charsets.UTF_8)
            )
        } catch (e: Exception) {
            false
        }
    }

    /** Milliseconds left in the cooldown, or 0 when an attempt may be made now. */
    @JvmStatic
    fun cooldownRemainingMs(): Long {
        val until = DefaultMultiProcessMMKVStorage().getLong(KEY_LOCKED_UNTIL, 0)
        val left = until - System.currentTimeMillis()
        return if (left > 0) left else 0
    }

    @JvmStatic
    fun noteSuccess() {
        val storage = DefaultMultiProcessMMKVStorage()
        storage.putLong(KEY_WRONG_ATTEMPTS, 0)
        storage.putLong(KEY_LOCKED_UNTIL, 0)
    }

    /**
     * Records one wrong attempt. The fifth starts a cooldown and clears the count, so the next
     * burst pays the same price. Kept in the store, not in a screen field, so restarting the app
     * does not reset it.
     */
    @JvmStatic
    fun noteFailure() {
        val storage = DefaultMultiProcessMMKVStorage()
        storage.lock()
        try {
            val attempts = storage.getLong(KEY_WRONG_ATTEMPTS, 0) + 1
            if (attempts >= MAX_ATTEMPTS) {
                storage.putLong(KEY_WRONG_ATTEMPTS, 0)
                storage.putLong(KEY_LOCKED_UNTIL, System.currentTimeMillis() + COOLDOWN_MS)
            } else {
                storage.putLong(KEY_WRONG_ATTEMPTS, attempts)
            }
        } finally {
            storage.unlock()
        }
    }

    private fun hash(salt: ByteArray, code: String): String {
        val sha = MessageDigest.getInstance("SHA-256")
        var out = sha.digest(concat(salt, code.toByteArray(Charsets.UTF_8)))
        for (i in 0 until ROUNDS) {
            out = sha.digest(concat(out, salt))
        }
        return Base64.encodeToString(out, Base64.NO_WRAP)
    }

    private fun concat(a: ByteArray, b: ByteArray): ByteArray {
        val out = ByteArray(a.size + b.size)
        System.arraycopy(a, 0, out, 0, a.size)
        System.arraycopy(b, 0, out, a.size, b.size)
        return out
    }
}