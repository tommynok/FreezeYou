package cf.playhi.freezeyou.utils

import android.app.Application
import cf.playhi.freezeyou.ui.SelectTargetActivityActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A shortcut stores its target as text. Two of the choices are offered in the user's language, so
 * the conversion between what the field shows and what gets stored is the point where a change of
 * language, or of wording, used to break every shortcut made earlier.
 *
 * The labels are passed in rather than read from resources, which is what the overload under test
 * is for — Context.getString is final and cannot be stood in for.
 */
class ShortcutTargetTest {

    private val ruLaunch = "Запуск"
    private val ruOnlyUnfreeze = "Только разморозить"
    private val enLaunch = "Run"
    private val enOnlyUnfreeze = "Only Unfreeze"

    private fun inRussian(target: String?) =
        FUFUtils.normalizeSelectedTarget(target, ruLaunch, ruOnlyUnfreeze)

    private fun inEnglish(target: String?) =
        FUFUtils.normalizeSelectedTarget(target, enLaunch, enOnlyUnfreeze)

    @Test
    fun `launch is stored as no target at all`() {
        assertNull(inRussian(ruLaunch))
        assertNull(inEnglish(enLaunch))
    }

    @Test
    fun `only unfreeze is stored as a marker that carries no language`() {
        assertEquals(FUFUtils.ONLY_UNFREEZE_TARGET, inRussian(ruOnlyUnfreeze))
        assertEquals(FUFUtils.ONLY_UNFREEZE_TARGET, inEnglish(enOnlyUnfreeze))
    }

    @Test
    fun `a shortcut made in one language is still understood in another`() {
        val stored = inRussian(ruOnlyUnfreeze)
        // The device language changes; the stored value must not stop being recognised.
        assertTrue(FUFUtils.isOnlyUnfreezeTarget(stored))
    }

    @Test
    fun `an activity class name is kept exactly as typed`() {
        val target = "com.looker.droidify.MainActivity"
        assertEquals(target, inRussian(target))
        assertFalse(FUFUtils.isOnlyUnfreezeTarget(target))
    }

    @Test
    fun `the marker cannot be confused with a class name`() {
        // '@' is not valid in a Java identifier, so no real activity can collide with it.
        assertTrue(FUFUtils.ONLY_UNFREEZE_TARGET.contains('@'))
    }

    @Test
    fun `no target and unknown text are not taken for only-unfreeze`() {
        assertNull(inRussian(null))
        assertFalse(FUFUtils.isOnlyUnfreezeTarget(null))
        assertFalse(FUFUtils.isOnlyUnfreezeTarget(""))
        // The wording this choice used to be stored as, before it had a stable value.
        assertFalse(FUFUtils.isOnlyUnfreezeTarget("Только размороженые"))
    }
}

/**
 * A pinned shortcut is identified by its id: the same id replaces the existing pin, a new one
 * adds a second shortcut beside it. The two standing choices are labelled with translated
 * strings, so an id built from the label changes with the language and leaves duplicates behind.
 *
 * The Activity runs on Robolectric only because the id builder lives there; the test itself has
 * no Android dependencies beyond loading that class.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ShortcutIdTest {

    private val pkgName = "com.looker.droidify"

    private fun id(targetKey: String?, displayName: String) =
        SelectTargetActivityActivity.buildShortcutId(pkgName, targetKey, displayName)

    @Test
    fun `the launch choice keeps one id across languages`() {
        assertEquals(
            id(SelectTargetActivityActivity.LAUNCH_TARGET_KEY, "Запуск"),
            id(SelectTargetActivityActivity.LAUNCH_TARGET_KEY, "Run")
        )
        assertEquals(
            "FreezeYou!$pkgName @launch",
            id(SelectTargetActivityActivity.LAUNCH_TARGET_KEY, "Run")
        )
    }

    @Test
    fun `the only-unfreeze choice keeps one id across languages`() {
        assertEquals(
            id(FUFUtils.ONLY_UNFREEZE_TARGET, "Только разморозить"),
            id(FUFUtils.ONLY_UNFREEZE_TARGET, "Only Unfreeze")
        )
        assertEquals(
            "FreezeYou!$pkgName @onlyUnfreeze",
            id(FUFUtils.ONLY_UNFREEZE_TARGET, "Only Unfreeze")
        )
    }

    @Test
    fun `an activity id is its class name in every language`() {
        val activityName = "com.looker.droidify.MainActivity"
        assertEquals(
            "FreezeYou!$pkgName $activityName",
            id(activityName, "Магазин")
        )
        assertEquals(
            id(activityName, "Магазин"),
            id(activityName, "Store")
        )
    }

    @Test
    fun `a row without a stable key falls back to its displayed name`() {
        assertEquals("FreezeYou!$pkgName Store", id(null, "Store"))
    }
}
