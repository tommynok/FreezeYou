package cf.playhi.freezeyou.utils

import android.app.Application
import android.content.Intent
import cf.playhi.freezeyou.storage.mmkv.DefaultMultiProcessMMKVStorage
import com.tencent.mmkv.MMKV
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.annotation.Resetter
import org.robolectric.shadows.ShadowSystemClock
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter
import java.time.Duration
import java.util.UUID
import java.util.concurrent.locks.ReentrantLock

@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [34], application = Application::class,
    shadows = [ShadowContractMMKV::class],
    instrumentedPackages = ["com.tencent.mmkv"]
)
class ShortcutTokenUtilsTest {
    private val actions = listOf(
        ShortcutTokenUtils.ACTION_FREEZE,
        ShortcutTokenUtils.ACTION_FORCE_STOP,
        ShortcutTokenUtils.ACTION_ONE_KEY_FREEZE,
        ShortcutTokenUtils.ACTION_ONE_KEY_UF,
        ShortcutTokenUtils.ACTION_LOCK_SCREEN
    )

    @Before
    fun resetStorage() {
        ShadowContractMMKV.reset()
    }

    @Test
    fun `creation persists one UUID per action and repeated reads reuse it`() {
        val tokens = actions.map { action ->
            val token = ShortcutTokenUtils.getOrCreateAuthToken(action)
            assertEquals(token, UUID.fromString(token).toString())
            assertEquals(token, DefaultMultiProcessMMKVStorage().getString("shortcutAuthToken_$action", null))
            assertEquals(token, ShortcutTokenUtils.getOrCreateAuthToken(action))
            token
        }
        assertEquals(actions.size, tokens.toSet().size)
        assertEquals(actions.size, ShadowContractMMKV.syncCount)
    }

    @Test
    fun `attachment preserves the intent package action and target extras`() {
        val intent = Intent(Intent.ACTION_VIEW).setPackage("cf.playhi.freezeyou")
            .putExtra("pkgName", "com.example.target")
            .putExtra("target", FUFUtils.ONLY_UNFREEZE_TARGET)
        assertSame(intent, ShortcutTokenUtils.attachToken(intent, ShortcutTokenUtils.ACTION_FREEZE))
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("cf.playhi.freezeyou", intent.`package`)
        assertEquals("com.example.target", intent.getStringExtra("pkgName"))
        assertEquals(FUFUtils.ONLY_UNFREEZE_TARGET, intent.getStringExtra("target"))
        assertTrue(ShortcutTokenUtils.verifyToken(intent, ShortcutTokenUtils.ACTION_FREEZE))
    }

    @Test
    fun `each action accepts its own token and rejects every other action token`() {
        for (action in actions) {
            val intent = ShortcutTokenUtils.attachToken(Intent(), action)
            for (requestedAction in actions) {
                assertEquals(
                    "$action token checked as $requestedAction",
                    action == requestedAction,
                    ShortcutTokenUtils.verifyToken(intent, requestedAction)
                )
            }
        }
    }

    @Test
    fun `null and missing tokens are rejected without creating credentials`() {
        assertFalse(ShortcutTokenUtils.verifyToken(null, ShortcutTokenUtils.ACTION_FREEZE))
        assertFalse(ShortcutTokenUtils.verifyToken(Intent(), ShortcutTokenUtils.ACTION_FREEZE))
        assertTrue(ShadowContractMMKV.strings.isEmpty())
    }

    @Test
    fun `empty malformed truncated and tampered tokens are rejected`() {
        val action = ShortcutTokenUtils.ACTION_FREEZE
        val valid = ShortcutTokenUtils.getOrCreateAuthToken(action)
        val changedFirstCharacter = (if (valid[0] == '0') "1" else "0") + valid.substring(1)
        for (invalid in listOf("", " ", "not-a-token", valid.dropLast(1), "$valid-extra", changedFirstCharacter)) {
            val intent = Intent().putExtra(ShortcutTokenUtils.EXTRA_AUTH_TOKEN, invalid)
            assertFalse("Accepted invalid token: $invalid", ShortcutTokenUtils.verifyToken(intent, action))
        }
        assertTrue(ShortcutTokenUtils.verifyToken(
            Intent().putExtra(ShortcutTokenUtils.EXTRA_AUTH_TOKEN, valid), action
        ))
    }

    @Test
    fun `replacing the stored credential rejects the old token but not another action`() {
        val action = ShortcutTokenUtils.ACTION_FREEZE
        val oldIntent = ShortcutTokenUtils.attachToken(Intent(), action)
        val otherIntent = ShortcutTokenUtils.attachToken(Intent(), ShortcutTokenUtils.ACTION_FORCE_STOP)
        val replacement = "00000000-0000-4000-8000-000000000001"
        DefaultMultiProcessMMKVStorage().putString("shortcutAuthToken_$action", replacement)

        assertFalse(ShortcutTokenUtils.verifyToken(oldIntent, action))
        assertTrue(ShortcutTokenUtils.verifyToken(otherIntent, ShortcutTokenUtils.ACTION_FORCE_STOP))
        val refreshed = ShortcutTokenUtils.attachToken(oldIntent, action)
        assertEquals(replacement, refreshed.getStringExtra(ShortcutTokenUtils.EXTRA_AUTH_TOKEN))
        assertTrue(ShortcutTokenUtils.verifyToken(refreshed, action))
    }

    @Test
    fun `an empty stored credential is regenerated and the old token is rejected`() {
        val action = ShortcutTokenUtils.ACTION_FREEZE
        val oldIntent = ShortcutTokenUtils.attachToken(Intent(), action)
        val oldToken = oldIntent.getStringExtra(ShortcutTokenUtils.EXTRA_AUTH_TOKEN)
        DefaultMultiProcessMMKVStorage().putString("shortcutAuthToken_$action", "")

        assertFalse(ShortcutTokenUtils.verifyToken(oldIntent, action))
        val fresh = ShortcutTokenUtils.attachToken(Intent(), action)
        assertNotEquals(oldToken, fresh.getStringExtra(ShortcutTokenUtils.EXTRA_AUTH_TOKEN))
        assertTrue(ShortcutTokenUtils.verifyToken(fresh, action))
    }

    @Test
    fun `stored tokens have no time based expiry`() {
        val intent = ShortcutTokenUtils.attachToken(Intent(), ShortcutTokenUtils.ACTION_FREEZE)
        ShadowSystemClock.advanceBy(Duration.ofDays(365))
        assertTrue(ShortcutTokenUtils.verifyToken(intent, ShortcutTokenUtils.ACTION_FREEZE))
    }

    @Test
    fun `verification is action scoped and does not authenticate package fields`() {
        val intent = ShortcutTokenUtils.attachToken(
            Intent().setPackage("cf.playhi.freezeyou").putExtra("pkgName", "com.example.first"),
            ShortcutTokenUtils.ACTION_FREEZE
        )
        intent.setPackage("com.example.other")
        intent.putExtra("pkgName", "com.example.second")
        assertTrue(ShortcutTokenUtils.verifyToken(intent, ShortcutTokenUtils.ACTION_FREEZE))
    }
}

// Shadow MMKV itself so the storage interface's default sync method cannot reach native code.
@Implements(MMKV::class, isInAndroidSdk = false)
class ShadowContractMMKV {
    @Implementation
    fun decodeString(key: String, defValue: String?): String? = strings[key] ?: defValue

    @Implementation
    fun encode(key: String, value: String?): Boolean {
        if (value == null) strings.remove(key) else strings[key] = value
        return true
    }

    @Implementation
    fun decodeBool(key: String, defValue: Boolean): Boolean = defValue

    @Implementation
    fun lock() = storageLock.lock()

    @Implementation
    fun unlock() = storageLock.unlock()

    @Implementation
    fun sync() {
        syncCount++
    }

    companion object {
        val strings = mutableMapOf<String, String>()
        private val storageLock = ReentrantLock()
        var syncCount = 0
            private set

        @JvmStatic
        @Implementation
        fun mmkvWithID(id: String, mode: Int): MMKV {
            assertEquals(DefaultMultiProcessMMKVStorage.MMKV_ID, id)
            assertEquals(MMKV.MULTI_PROCESS_MODE, mode)
            return ReflectionHelpers.callConstructor(
                MMKV::class.java, ClassParameter.from(Long::class.javaPrimitiveType!!, 0L)
            )
        }

        @JvmStatic
        @Resetter
        fun reset() {
            strings.clear()
            syncCount = 0
        }
    }
}
