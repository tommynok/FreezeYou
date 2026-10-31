package cf.playhi.freezeyou

import android.app.Activity
import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The confirmation dialogs of the main screen used to ask "Yes / No" where Android asks
 * "OK / Cancel", and a cancelled system uninstall prompt used to be answered by throwing the
 * next package's prompt at the user until the queue ran out. Both are wording and flow the
 * owner judges by eye on the device, so the contract is pinned here: the canonical strings the
 * dialogs now use, and the rule for when one system prompt ends the whole batch.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "ru-rRU", application = Application::class)
class MainDialogsAndUninstallTest {

    @Test
    fun `the dialog buttons of this batch are the canonical OK and Cancel`() {
        val app = RuntimeEnvironment.getApplication()
        assertEquals("ОК", app.getString(R.string.okay))
        assertEquals("Отмена", app.getString(R.string.cancel))
    }

    @Test
    fun `a back press out of the system prompt ends the whole uninstall batch`() {
        assertTrue(Main.abortUninstallBatchOnCancel(Activity.RESULT_CANCELED, true))
    }

    @Test
    fun `a cancelled result for a package that is gone keeps the batch going`() {
        // The uninstaller answers RESULT_CANCELED by default, so a successful removal arrives
        // looking like a back press; treating it as one stopped the batch after the first app.
        assertFalse(Main.abortUninstallBatchOnCancel(Activity.RESULT_CANCELED, false))
    }

    @Test
    fun `a confirmed or failed system prompt keeps the batch going`() {
        assertFalse(Main.abortUninstallBatchOnCancel(Activity.RESULT_OK, true))
        assertFalse(Main.abortUninstallBatchOnCancel(Activity.RESULT_FIRST_USER, true))
    }
}
