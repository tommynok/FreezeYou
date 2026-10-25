package cf.playhi.freezeyou

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
 * Bulk freezing asked before touching an application the device cannot do without; freezing the
 * same one from its own row did not, which is the easier mistake to make and the harder one to
 * undo. The rule the dialog is built on is pinned here, together with the warning the user
 * actually reads: the single-action path shares the bulk path's strings, and a wording drift
 * between the two is invisible until somebody is locked out.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "ru-rRU", application = Application::class)
class FreezeCriticalSafeguardTest {

    @Test
    fun `freezing a critical application is asked about first`() {
        assertTrue(Freeze.needsCriticalFreezeConfirmation(false, true))
    }

    @Test
    fun `freezing an ordinary application goes through without a question`() {
        assertFalse(Freeze.needsCriticalFreezeConfirmation(false, false))
    }

    @Test
    fun `unfreezing a critical application is never blocked by the question`() {
        // Removing the freeze is the way back to a missing status bar, Settings app or keyboard.
        assertFalse(Freeze.needsCriticalFreezeConfirmation(true, true))
    }

    @Test
    fun `the single action warning names the application inside the bulk warning text`() {
        val app = RuntimeEnvironment.getApplication()
        val line = "• System UI (com.android.systemui)"
        val message = app.getString(R.string.critical_package_freeze_warning_single, line)
        assertTrue(message.contains(line))
        // Both buttons are the canonical ones, the same pair the bulk warning and the rest of
        // the app use.
        assertEquals("ВНИМАНИЕ", app.getString(R.string.caution))
        assertEquals("ОК", app.getString(R.string.okay))
        assertEquals("Отмена", app.getString(R.string.cancel))
    }
}
