package cf.playhi.freezeyou.ui

import android.app.Application
import cf.playhi.freezeyou.R
import cf.playhi.freezeyou.utils.LauncherShortcutUtils.ShortcutRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "ru-rRU", application = Application::class)
class RestoreShortcutsActivityTest {

    @Test
    fun filterUnpinnedShortcutsRetainsAllWhenPinnedSetIsNull() {
        val records = listOf(
            ShortcutRecord("id_1", "App 1", "com.pkg1", "cf.playhi.freezeyou.Freeze"),
            ShortcutRecord("id_2", "App 2", "com.pkg2", "cf.playhi.freezeyou.Freeze")
        )
        val filtered = RestoreShortcutsActivity.filterUnpinnedShortcuts(records, null)
        assertEquals(2, filtered.size)
        assertEquals(records, filtered)
    }

    @Test
    fun filterUnpinnedShortcutsExcludesAlreadyPinnedShortcuts() {
        val records = listOf(
            ShortcutRecord("id_1", "App 1", "com.pkg1", "cf.playhi.freezeyou.Freeze"),
            ShortcutRecord("id_2", "App 2", "com.pkg2", "cf.playhi.freezeyou.Freeze"),
            ShortcutRecord("id_3", "App 3", "com.pkg3", "cf.playhi.freezeyou.Freeze")
        )
        val pinned = setOf("id_1", "id_3")
        val filtered = RestoreShortcutsActivity.filterUnpinnedShortcuts(records, pinned)

        assertEquals(1, filtered.size)
        assertEquals("id_2", filtered[0].id)
    }

    @Test
    fun filterUnpinnedShortcutsReturnsEmptyWhenAllArePinned() {
        val records = listOf(
            ShortcutRecord("id_1", "App 1", "com.pkg1", "cf.playhi.freezeyou.Freeze")
        )
        val pinned = setOf("id_1")
        val filtered = RestoreShortcutsActivity.filterUnpinnedShortcuts(records, pinned)

        assertTrue(filtered.isEmpty())
    }

    @Test
    fun restoreShortcutFailedStringFormattingInRussian() {
        val app = RuntimeEnvironment.getApplication()
        val formatted = app.getString(R.string.restoreShortcutFailed, "Telegram")
        assertEquals("Не удалось добавить ярлык «Telegram»", formatted)
    }
}
