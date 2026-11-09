package cf.playhi.freezeyou.utils

import android.os.Handler
import android.os.Looper
import android.util.Base64
import cf.playhi.freezeyou.storage.mmkv.DefaultMultiProcessMMKVStorage
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.Executors

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

    /**
     * One worker for the slow part - the fifty thousand rounds - so tapping OK never blocks the
     * thread that draws the button. The answer is handed back on the main thread, where the dialog
     * is dismissed and the screen navigates; the counters are still read and written there too.
     */
    private val worker = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    /** A verification answer, delivered on the main thread. A SAM so Java callers pass a lambda. */
    fun interface VerifyCallback {
        fun onResult(ok: Boolean)
    }

    /** A setting completed, delivered on the main thread. */
    fun interface DoneCallback {
        fun onDone()
    }

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

    /** Sets the code off the main thread; [callback] runs on the main thread once it is stored. */
    @JvmStatic
    fun setCodeAsync(code: String, callback: DoneCallback) {
        worker.execute {
            setCode(code)
            mainHandler.post { callback.onDone() }
        }
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

    /** Verifies off the main thread; [callback] runs on the main thread with the answer. */
    @JvmStatic
    fun verifyCodeAsync(code: String, callback: VerifyCallback) {
        worker.execute {
            val ok = verifyCode(code)
            mainHandler.post { callback.onResult(ok) }
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
        val digestSize = sha.digestLength
        // One buffer for the whole run: the running digest followed by the salt. The salt half is
        // written once and never moves; each round feeds the buffer back in and overwrites only the
        // digest half with the result, so fifty thousand rounds allocate nothing. The bytes fed in
        // are the same salt||code then out||salt as before, so a stored code still verifies.
        val buffer = ByteArray(digestSize + salt.size)
        System.arraycopy(salt, 0, buffer, digestSize, salt.size)
        sha.update(salt)
        sha.update(code.toByteArray(Charsets.UTF_8))
        sha.digest(buffer, 0, digestSize)
        for (i in 0 until ROUNDS) {
            sha.update(buffer)
            sha.digest(buffer, 0, digestSize)
        }
        return Base64.encodeToString(buffer, 0, digestSize, Base64.NO_WRAP)
    }
}