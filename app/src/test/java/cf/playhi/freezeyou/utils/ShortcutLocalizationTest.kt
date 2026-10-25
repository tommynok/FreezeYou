package cf.playhi.freezeyou.utils

import android.app.Application
import cf.playhi.freezeyou.R
import cf.playhi.freezeyou.ui.SelectTargetActivityActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The launcher draws this app's shortcuts from its own copy of the process resources, so a
 * language chosen inside the app never reached them: the labels were read through the plain
 * context, which carries the system locale. ShortcutTokenUtils now reads them through a context
 * re-created with the chosen locale, and on Android 13+ tells the system the per-app locale so
 * the static shortcuts of res/xml/shortcuts.xml follow as well.
 *
 * The labels are asserted as the literal resource values ("Onekey Freeze", "OneKey-заморозка"):
 * the wording is part of the contract, because a label republished in another language is what
 * this batch fixes, and only the exact strings show whether the localized context took effect.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ShortcutLocalizationTest {

    private fun labelsIn(localeTag: String): Pair<String, String> {
        val localized = ShortcutTokenUtils.localizedContext(
            RuntimeEnvironment.getApplication(), localeTag
        )
        return localized.getString(R.string.oneKeyFreeze) to localized.getString(R.string.oneKeyUF)
    }

    @Test
    fun `the one key shortcut labels follow the language chosen in the app`() {
        assertEquals("Onekey Freeze", labelsIn("en").first)
        assertEquals("Onekey Unfreeze", labelsIn("en").second)
        assertEquals("OneKey-заморозка", labelsIn("ru-RU").first)
        assertEquals("OneKey-разморозка", labelsIn("ru-RU").second)
        assertNotEquals(labelsIn("en"), labelsIn("ru-RU"))
    }

    @Test
    fun `the default tag keeps following the device language`() {
        val plain = RuntimeEnvironment.getApplication()
        assertEquals(
            plain.getString(R.string.oneKeyFreeze) to plain.getString(R.string.oneKeyUF),
            labelsIn("Default")
        )
    }

    @Test
    fun `shortcut ids stay literals so a language change never duplicates a pin`() {
        assertEquals("onekeyFreeze", ShortcutTokenUtils.SHORTCUT_ID_ONE_KEY_FREEZE)
        assertEquals("onekeyUF", ShortcutTokenUtils.SHORTCUT_ID_ONE_KEY_UF)
        // The pinned target shortcuts keep their own scheme: the id comes from the target key,
        // never from the translated row label.
        val pkgName = "com.looker.droidify"
        assertEquals(
            SelectTargetActivityActivity.buildShortcutId(
                pkgName, SelectTargetActivityActivity.LAUNCH_TARGET_KEY, "Запуск"
            ),
            SelectTargetActivityActivity.buildShortcutId(
                pkgName, SelectTargetActivityActivity.LAUNCH_TARGET_KEY, "Run"
            )
        )
    }
}
