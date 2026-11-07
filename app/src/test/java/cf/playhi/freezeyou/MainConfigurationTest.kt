package cf.playhi.freezeyou

import android.app.Application
import android.content.res.Configuration
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The main screen resolved its list/grid mode once, in go(), while the manifest handles rotation
 * itself, so the automatic pattern never followed the device. resolveGridMode is the rule the
 * launch path and onConfigurationChanged now share, and these tests pin it down so the two
 * callers cannot drift apart: an explicit choice always wins, the automatic one follows the
 * orientation, and only a configuration without an orientation falls back to the aspect ratio.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class MainConfigurationTest {

    @Test
    fun `an explicit grid choice ignores the orientation`() {
        assertTrue(Main.resolveGridMode("grid", Configuration.ORIENTATION_PORTRAIT, 1080, 1920))
        assertTrue(Main.resolveGridMode("grid", Configuration.ORIENTATION_LANDSCAPE, 1920, 1080))
        assertTrue(Main.resolveGridMode("grid", Configuration.ORIENTATION_UNDEFINED, 0, 0))
    }

    @Test
    fun `an explicit list choice ignores the orientation`() {
        assertFalse(Main.resolveGridMode("list", Configuration.ORIENTATION_PORTRAIT, 1080, 1920))
        assertFalse(Main.resolveGridMode("list", Configuration.ORIENTATION_LANDSCAPE, 1920, 1080))
        assertFalse(Main.resolveGridMode("list", Configuration.ORIENTATION_UNDEFINED, 0, 0))
    }

    @Test
    fun `the automatic pattern turns to grid in landscape`() {
        assertTrue(Main.resolveGridMode("default", Configuration.ORIENTATION_LANDSCAPE, 1920, 1080))
        // A freeform window can stay landscape while being taller than it is wide; the automatic
        // pattern follows the orientation, not the window shape.
        assertTrue(Main.resolveGridMode("default", Configuration.ORIENTATION_LANDSCAPE, 600, 1000))
    }

    @Test
    fun `the automatic pattern returns to the list in portrait`() {
        assertFalse(Main.resolveGridMode("default", Configuration.ORIENTATION_PORTRAIT, 1080, 1920))
        assertFalse(Main.resolveGridMode("default", Configuration.ORIENTATION_PORTRAIT, 2000, 1000))
    }

    @Test
    fun `the automatic pattern falls back to a wide aspect ratio without an orientation`() {
        assertTrue(Main.resolveGridMode("default", Configuration.ORIENTATION_UNDEFINED, 1920, 1080))
        assertFalse(Main.resolveGridMode("default", Configuration.ORIENTATION_UNDEFINED, 1080, 1920))
    }

    @Test
    fun `square and degenerate windows stay in the list`() {
        assertFalse(Main.resolveGridMode("default", Configuration.ORIENTATION_UNDEFINED, 1080, 1080))
        // The threshold is exclusive: 1200 is exactly 1000 * 1.2.
        assertFalse(Main.resolveGridMode("default", Configuration.ORIENTATION_UNDEFINED, 1200, 1000))
        assertFalse(Main.resolveGridMode("default", Configuration.ORIENTATION_UNDEFINED, 1920, 0))
    }
}
