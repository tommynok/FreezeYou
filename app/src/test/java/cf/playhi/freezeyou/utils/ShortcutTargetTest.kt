package cf.playhi.freezeyou.utils

import android.content.Context
import android.content.ContextWrapper
import cf.playhi.freezeyou.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A shortcut stores its target as text. Two of the choices are offered in the user's language, so
 * the conversion between what the field shows and what gets stored is the point where a change of
 * language, or of wording, used to break every shortcut made earlier.
 */
class ShortcutTargetTest {

    /**
     * Only [getString] is ever called here. ContextWrapper supplies the rest of Context, and the
     * unit-test android.jar returns defaults instead of throwing, so no mocking library is needed.
     */
    private class StringsOnlyContext(
        private val strings: Map<Int, String>
    ) : ContextWrapper(null) {
        override fun getString(resId: Int): String = strings[resId] ?: "?$resId"
    }

    private fun contextOf(launch: String, onlyUnfreeze: String): Context =
        StringsOnlyContext(
            mapOf(R.string.launch to launch, R.string.onlyUnfreeze to onlyUnfreeze)
        )

    private val russian = contextOf("Запуск", "Только разморозить")
    private val english = contextOf("Run", "Only Unfreeze")

    @Test
    fun `launch is stored as no target at all`() {
        assertNull(FUFUtils.normalizeSelectedTarget(russian, "Запуск"))
        assertNull(FUFUtils.normalizeSelectedTarget(english, "Run"))
    }

    @Test
    fun `only unfreeze is stored as a marker that carries no language`() {
        assertEquals(
            FUFUtils.ONLY_UNFREEZE_TARGET,
            FUFUtils.normalizeSelectedTarget(russian, "Только разморозить")
        )
        assertEquals(
            FUFUtils.ONLY_UNFREEZE_TARGET,
            FUFUtils.normalizeSelectedTarget(english, "Only Unfreeze")
        )
    }

    @Test
    fun `a shortcut made in one language is still understood in another`() {
        val stored = FUFUtils.normalizeSelectedTarget(russian, "Только разморозить")
        // The device language changes; the stored value must not stop being recognised.
        assertTrue(FUFUtils.isOnlyUnfreezeTarget(stored))
    }

    @Test
    fun `an activity class name is kept exactly as typed`() {
        val target = "com.looker.droidify.MainActivity"
        assertEquals(target, FUFUtils.normalizeSelectedTarget(russian, target))
        assertFalse(FUFUtils.isOnlyUnfreezeTarget(target))
    }

    @Test
    fun `the marker cannot be confused with a class name`() {
        // '@' is not valid in a Java identifier, so no real activity can collide with it.
        assertTrue(FUFUtils.ONLY_UNFREEZE_TARGET.contains('@'))
    }

    @Test
    fun `no target and unknown text are not taken for only-unfreeze`() {
        assertNull(FUFUtils.normalizeSelectedTarget(russian, null))
        assertFalse(FUFUtils.isOnlyUnfreezeTarget(null))
        assertFalse(FUFUtils.isOnlyUnfreezeTarget(""))
        // The wording this choice used to be stored as, before it had a stable value.
        assertFalse(FUFUtils.isOnlyUnfreezeTarget("Только размороженые"))
    }
}
