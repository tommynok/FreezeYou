package cf.playhi.freezeyou

import android.app.Application
import android.view.ContextThemeWrapper
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import cf.playhi.freezeyou.adapter.MainAppListSimpleAdapter
import cf.playhi.freezeyou.utils.RunningAppsUtils
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23, 34], application = Application::class)
class MainAppListStatsTest {
    @Test
    fun recycledListRowsShowAndClearStats() {
        checkRecycledRows(R.layout.app_list_1, R.id.name, R.id.img)
    }

    @Test
    fun recycledGridRowsShowAndClearStats() {
        checkRecycledRows(R.layout.main_grid_main_item, R.id.mgmi_textView, R.id.mgmi_imageView)
    }

    @Test
    fun unavailableMeasurementsAreNotShownAsZero() {
        val context = RuntimeEnvironment.getApplication()
        assertEquals(
            context.getString(R.string.runningAppStats, "—", "—"),
            RunningAppsUtils.formatResourceUsage(context, null)
        )
    }

    private fun checkRecycledRows(layout: Int, nameId: Int, iconId: Int) {
        val context = ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.AppTheme_Default)
        val stats = context.getString(R.string.runningAppStats, "12.0%", "1 MB")
        val rows = arrayListOf<Map<String, Any>>(
            mapOf("Name" to "First", "RunningStats" to stats),
            mapOf("Name" to "Second")
        )
        val adapter = MainAppListSimpleAdapter(
            context, rows, arrayListOf(), layout, arrayOf("Name"), intArrayOf(nameId), iconId, false
        )
        val parent = FrameLayout(context)
        val row = adapter.getView(0, null, parent)
        val label = row.findViewById<TextView>(R.id.running_app_stats)
        assertEquals(View.VISIBLE, label.visibility)
        assertEquals(stats, label.text.toString())
        adapter.getView(1, row, parent)
        assertEquals(View.GONE, label.visibility)
        assertEquals("", label.text.toString())
        adapter.getView(0, row, parent)
        assertEquals(View.VISIBLE, label.visibility)
        assertEquals(stats, label.text.toString())
    }
}
