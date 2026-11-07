package cf.playhi.freezeyou

import android.app.Application
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A package status broadcast used to patch the changed row in place. In a status-dependent filter
 * that is not enough: freezing or unfreezing an app moves it out of the current list, so the row
 * stayed behind until the user switched filters by hand. isStatusDependentFilter is the rule the
 * receiver now shares with the list builder, and these tests pin down which filters it covers.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class MainStatusUpdateTest {

    @Test
    fun `the frozen and unfrozen filters depend on the status`() {
        assertTrue(Main.isStatusDependentFilter("OF"))
        assertTrue(Main.isStatusDependentFilter("UF"))
        assertTrue(Main.isStatusDependentFilter("UFU"))
    }

    @Test
    fun `the running filters depend on the status`() {
        assertTrue(Main.isStatusDependentFilter("RUN"))
        assertTrue(Main.isStatusDependentFilter("RUN_SYS"))
        assertTrue(Main.isStatusDependentFilter("RUN_USER"))
    }

    @Test
    fun `the remaining built-in filters only change with the list itself`() {
        assertFalse(Main.isStatusDependentFilter("all"))
        assertFalse(Main.isStatusDependentFilter("OO"))
        assertFalse(Main.isStatusDependentFilter("OOU"))
        assertFalse(Main.isStatusDependentFilter("FOQ"))
        assertFalse(Main.isStatusDependentFilter("OS"))
        assertFalse(Main.isStatusDependentFilter("OUS"))
        assertFalse(Main.isStatusDependentFilter("OU"))
    }

    @Test
    fun `a missing filter is not status dependent`() {
        assertFalse(Main.isStatusDependentFilter(null))
        assertFalse(Main.isStatusDependentFilter(""))
        // Custom categories are stored as Base64 labels and never start with a built-in prefix.
        assertFalse(Main.isStatusDependentFilter("Y2F0ZWdvcnk="))
    }

    @Test
    fun `the filter names are matched exactly`() {
        assertFalse(Main.isStatusDependentFilter("of"))
        assertFalse(Main.isStatusDependentFilter("UFU2"))
    }
}
