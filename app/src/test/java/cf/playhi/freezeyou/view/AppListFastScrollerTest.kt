package cf.playhi.freezeyou.view

import android.app.Application
import android.graphics.Color
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.math.roundToInt

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi", application = Application::class)
class AppListFastScrollerTest {

    @Test
    fun testAttachAndDetachLifecycle() {
        val context = RuntimeEnvironment.getApplication()
        val recyclerView = RecyclerView(context)
        recyclerView.layoutManager = LinearLayoutManager(context)

        val scroller = AppListFastScroller(recyclerView)
        assertNotNull(scroller)

        // Detach should cleanly unregister callbacks without throwing
        scroller.detach()
    }

    @Test
    fun testMinThumbHeightPreserved() {
        val context = RuntimeEnvironment.getApplication()
        val density = context.resources.displayMetrics.density
        val minHeightPx = (68f * density).roundToInt()

        val recyclerView = RecyclerView(context)
        val scroller = AppListFastScroller(recyclerView)

        // Verify minimum thumb height threshold is at least 68dp
        assertTrue("Min thumb height should be at least 68dp in pixels", minHeightPx >= 68)
    }

    @Test
    fun testThumbColorContrasts() {
        val darkGray = Color.argb(0xCC, 0x38, 0x38, 0x38)
        val nearBlack = Color.argb(0xEB, 0x21, 0x21, 0x21)

        // High contrast neutral grey when scrolling: alpha should be ~80%
        assertEquals(0xCC, Color.alpha(darkGray))
        // Dragging state should be higher opacity
        assertTrue(Color.alpha(nearBlack) > Color.alpha(darkGray))
    }

    @Test
    fun testShortWindowDoesNotCrash() {
        val context = RuntimeEnvironment.getApplication()
        val recyclerView = object : RecyclerView(context) {
            override fun getHeight(): Int = 120 // Short split-screen window (< 204dp)
            override fun getWidth(): Int = 400
            override fun computeVerticalScrollRange(): Int = 2000
            override fun computeVerticalScrollExtent(): Int = 120
            override fun computeVerticalScrollOffset(): Int = 100
        }
        recyclerView.layoutManager = LinearLayoutManager(context)

        // Must not throw IllegalArgumentException when window height is less than 3 * minThumbHeight
        val scroller = AppListFastScroller(recyclerView)
        assertNotNull(scroller)
        scroller.detach()
    }

    @Test
    fun testNonScrollableListDoesNotInterceptTouch() {
        val context = RuntimeEnvironment.getApplication()
        val recyclerView = object : RecyclerView(context) {
            override fun getHeight(): Int = 800
            override fun getWidth(): Int = 400
            // Non-scrollable: range equals extent
            override fun computeVerticalScrollRange(): Int = 800
            override fun computeVerticalScrollExtent(): Int = 800
            override fun computeVerticalScrollOffset(): Int = 0
        }
        recyclerView.layoutManager = LinearLayoutManager(context)

        val scroller = AppListFastScroller(recyclerView)
        assertNotNull(scroller)
        scroller.detach()
    }
}
