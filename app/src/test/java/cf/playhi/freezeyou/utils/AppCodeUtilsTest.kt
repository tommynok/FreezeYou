package cf.playhi.freezeyou.utils

import android.app.Application
import cf.playhi.freezeyou.storage.mmkv.DefaultMultiProcessMMKVStorage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The code built into FreezeYou, checked the way the token store is: over the same MMKV contract
 * shadow, so the round trip a device gives the secret is the round trip the test reads back.
 *
 * What is worth pinning down is the part that only shows up once it has gone wrong on a device: the
 * code is never kept in the clear, a wrong code is refused, the attempt counter lives in the store
 * rather than in a screen field so restarting the app does not hand a guesser a fresh set of tries,
 * and a non-ASCII code survives the hash because it is fed in as UTF-8 bytes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [34], application = Application::class,
    shadows = [ShadowContractMMKV::class],
    instrumentedPackages = ["com.tencent.mmkv"]
)
class AppCodeUtilsTest {

    @Before
    fun resetStorage() {
        ShadowContractMMKV.reset()
    }

    @Test
    fun `a fresh install has no code and refuses every candidate`() {
        assertFalse(AppCodeUtils.hasCode())
        assertFalse(AuthenticationUtils.isCodeSet())
        assertFalse(AppCodeUtils.verifyCode("1234"))
        assertFalse(AppCodeUtils.verifyCode(""))
    }

    @Test
    fun `a set code verifies and a wrong or empty one does not`() {
        AppCodeUtils.setCode("1234")
        assertTrue(AppCodeUtils.hasCode())
        assertTrue(AuthenticationUtils.isCodeSet())
        assertTrue(AppCodeUtils.verifyCode("1234"))
        assertFalse(AppCodeUtils.verifyCode("1235"))
        assertFalse(AppCodeUtils.verifyCode(""))
        assertFalse(AppCodeUtils.verifyCode("12345"))
    }

    @Test
    fun `the store keeps a salted hash rather than the code`() {
        AppCodeUtils.setCode("1234")
        val stored = DefaultMultiProcessMMKVStorage().getString("appCodeHash", null)!!
        assertTrue(stored.contains(':'))
        assertFalse(stored.contains("1234"))

        // A second setting draws a fresh salt, so the same code does not produce the same hash.
        AppCodeUtils.setCode("1234")
        val again = DefaultMultiProcessMMKVStorage().getString("appCodeHash", null)!!
        assertNotEquals(stored, again)
        assertTrue(AppCodeUtils.verifyCode("1234"))
    }

    @Test
    fun `a non-ASCII code round trips through the hash`() {
        AppCodeUtils.setCode("пароль")
        assertTrue(AppCodeUtils.verifyCode("пароль"))
        assertFalse(AppCodeUtils.verifyCode("password"))
    }

    @Test
    fun `the hash matches the algorithm shipped codes were stored with`() {
        // A fixed salt and code walked through the original fifty thousand SHA-256 rounds. Reusing
        // one buffer must not move a single byte, or a code set on an earlier build stops verifying.
        DefaultMultiProcessMMKVStorage().putString(
            "appCodeHash",
            "AAECAwQFBgcICQoLDA0ODw==:3rokXaD8p6Bb5GsgCEZN2Lo5xgkrkuCSILi2ntIdVts="
        )
        assertTrue(AppCodeUtils.hasCode())
        assertTrue(AppCodeUtils.verifyCode("1234"))
        assertFalse(AppCodeUtils.verifyCode("1235"))
    }

    @Test
    fun `clearing the code takes the protection with it`() {
        AppCodeUtils.setCode("1234")
        AppCodeUtils.clearCode()
        assertFalse(AppCodeUtils.hasCode())
        assertFalse(AppCodeUtils.verifyCode("1234"))
    }

    @Test
    fun `the fifth wrong attempt starts a cooldown kept in the store`() {
        AppCodeUtils.setCode("1234")
        for (i in 1..4) {
            AppCodeUtils.noteFailure()
            assertEquals(0L, AppCodeUtils.cooldownRemainingMs())
        }
        AppCodeUtils.noteFailure()
        assertTrue(AppCodeUtils.cooldownRemainingMs() > 0)

        // Persisted, not a screen field: a fresh reader sees the same cooldown, and the count has
        // started over so the next burst pays the same price.
        val until = DefaultMultiProcessMMKVStorage().getLong("appCodeLockedUntil", 0)
        assertTrue(until > System.currentTimeMillis())
        assertEquals(0L, DefaultMultiProcessMMKVStorage().getLong("appCodeWrongAttempts", -1))
    }

    @Test
    fun `a success clears the count and the cooldown`() {
        AppCodeUtils.setCode("1234")
        for (i in 1..5) AppCodeUtils.noteFailure()
        assertTrue(AppCodeUtils.cooldownRemainingMs() > 0)
        AppCodeUtils.noteSuccess()
        assertEquals(0L, AppCodeUtils.cooldownRemainingMs())
    }

    @Test
    fun `a cooldown whose time has passed no longer blocks`() {
        AppCodeUtils.setCode("1234")
        DefaultMultiProcessMMKVStorage()
            .putLong("appCodeLockedUntil", System.currentTimeMillis() - 1000)
        assertEquals(0L, AppCodeUtils.cooldownRemainingMs())
    }

    @Test
    fun `setting a code clears an earlier cooldown`() {
        for (i in 1..5) AppCodeUtils.noteFailure()
        assertTrue(AppCodeUtils.cooldownRemainingMs() > 0)
        AppCodeUtils.setCode("4321")
        assertEquals(0L, AppCodeUtils.cooldownRemainingMs())
        assertTrue(AppCodeUtils.verifyCode("4321"))
    }

    @Test
    fun `a malformed stored secret is refused rather than crashing`() {
        DefaultMultiProcessMMKVStorage().putString("appCodeHash", "not-a-valid-secret")
        assertFalse(AppCodeUtils.hasCode())
        assertFalse(AppCodeUtils.verifyCode("1234"))
    }
}