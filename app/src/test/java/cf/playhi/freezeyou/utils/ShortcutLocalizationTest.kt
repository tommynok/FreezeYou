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
 * context, which carries the system locale. ShortcutTokenUtils reads them through a context
 * re-created with the chosen locale instead — the app applies its language itself and does not hand
 * a per-app locale to the system, so nothing else could translate these labels.
 *
 * "scheduledTasks" is in the contract for a second reason: a static shortcut label is resolved from
 * the APK with the system locale, so it can never follow the language picked inside the app. The
 * block is gone from res/xml/shortcuts.xml and the shortcut is published dynamically, which leaves
 * only the app-name shortcut static.
 *
 * The labels are asserted as the literal resource values ("Onekey Freeze", "OneKey-заморозка"):
 * the wording is part of the contract, because a label republished in another language is what
 * this batch fixes, and only the exact strings show whether the localized context took effect.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ShortcutLocalizationTest {

    private fun labelsIn(localeTag: String): Triple<String, String, String> {
        val localized = ShortcutTokenUtils.localizedContext(
            RuntimeEnvironment.getApplication(), localeTag
        )
        return Triple(
            localized.getString(R.string.oneKeyFreeze),
            localized.getString(R.string.oneKeyUF),
            localized.getString(R.string.scheduledTasks)
        )
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
    fun `the scheduled tasks label follows the language chosen in the app`() {
        assertEquals("Scheduled tasks", labelsIn("en").third)
        assertEquals("Запланированные задания", labelsIn("ru-RU").third)
        assertNotEquals(labelsIn("en").third, labelsIn("ru-RU").third)
    }

    @Test
    fun `the default tag keeps following the device language`() {
        val plain = RuntimeEnvironment.getApplication()
        assertEquals(
            Triple(
                plain.getString(R.string.oneKeyFreeze),
                plain.getString(R.string.oneKeyUF),
                plain.getString(R.string.scheduledTasks)
            ),
            labelsIn("Default")
        )
    }

    @Test
    fun `shortcut ids stay literals so a language change never duplicates a pin`() {
        assertEquals("onekeyFreeze", ShortcutTokenUtils.SHORTCUT_ID_ONE_KEY_FREEZE)
        assertEquals("onekeyUF", ShortcutTokenUtils.SHORTCUT_ID_ONE_KEY_UF)
        // The id the removed static block used, so an install that already showed that entry
        // replaces it with the localized one instead of carrying a stale shortcut beside it.
        assertEquals("scheduledTasks", ShortcutTokenUtils.SHORTCUT_ID_SCHEDULED_TASKS)
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
