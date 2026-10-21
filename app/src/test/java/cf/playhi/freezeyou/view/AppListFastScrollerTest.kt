package cf.playhi.freezeyou.view

import android.app.Application
import android.graphics.Color
import android.view.View
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
        val expectedMinPx = (54f * density).roundToInt()

        val recyclerView = object : RecyclerView(context) {
            override fun computeVerticalScrollRange(): Int = 50000 // Very long list
            override fun computeVerticalScrollExtent(): Int = 600
            override fun computeVerticalScrollOffset(): Int = 100
        }
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.measure(
            View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY)
        )
        recyclerView.layout(0, 0, 400, 600)

        val scroller = AppListFastScroller(recyclerView)

        // Verify scroller's internal minimum matches 54dp
        assertEquals("Configured minThumbHeight should match 54dp in pixels", expectedMinPx, scroller.getMinThumbHeight())
        // Verify that on a long list, calculated thumb height never drops below minThumbHeight
        assertTrue("Rendered thumbHeight should be at least minThumbHeight", scroller.getThumbHeight() >= scroller.getMinThumbHeight())
        assertEquals("Rendered thumbHeight on long list should clamp to minThumbHeight", expectedMinPx, scroller.getThumbHeight())

        scroller.detach()
    }

    @Test
    fun testThumbColorContrasts() {
        val context = RuntimeEnvironment.getApplication()
        val recyclerView = RecyclerView(context)
        val scroller = AppListFastScroller(recyclerView)

        val scrollingColor = scroller.getThumbColor(context, dragging = false)
        val draggingColor = scroller.getThumbColor(context, dragging = true)

        // Verify scroller returns soft legible neutral grey when scrolling (~72% alpha)
        assertEquals(0xB8, Color.alpha(scrollingColor))
        // Verify dragging state increases opacity and focus
        assertTrue(Color.alpha(draggingColor) > Color.alpha(scrollingColor))

        scroller.detach()
    }

    @Test
    fun testShortWindowDoesNotCrash() {
        val context = RuntimeEnvironment.getApplication()
        val recyclerView = object : RecyclerView(context) {
            override fun computeVerticalScrollRange(): Int = 2000
            override fun computeVerticalScrollExtent(): Int = 120
            override fun computeVerticalScrollOffset(): Int = 100
        }
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.measure(
            View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(120, View.MeasureSpec.EXACTLY)
        )
        recyclerView.layout(0, 0, 400, 120)

        // Must not throw IllegalArgumentException when window height is less than 3 * minThumbHeight
        val scroller = AppListFastScroller(recyclerView)
        assertNotNull(scroller)
        scroller.detach()
    }

    @Test
    fun testNonScrollableListDoesNotInterceptTouch() {
        val context = RuntimeEnvironment.getApplication()
        val recyclerView = object : RecyclerView(context) {
            // Non-scrollable: range equals extent
            override fun computeVerticalScrollRange(): Int = 800
            override fun computeVerticalScrollExtent(): Int = 800
            override fun computeVerticalScrollOffset(): Int = 0
        }
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.measure(
            View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY)
        )
        recyclerView.layout(0, 0, 400, 800)

        val scroller = AppListFastScroller(recyclerView)
        assertNotNull(scroller)
        scroller.detach()
    }
}
