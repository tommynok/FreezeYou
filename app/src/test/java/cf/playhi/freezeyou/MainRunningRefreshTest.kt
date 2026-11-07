package cf.playhi.freezeyou

import android.app.Application
import android.content.SharedPreferences
import android.os.Looper
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
import androidx.preference.PreferenceManager
import cf.playhi.freezeyou.utils.ShadowContractMMKV
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23, 34], application = Application::class, shadows = [ShadowContractMMKV::class])
@LooperMode(LooperMode.Mode.PAUSED)
class MainRunningRefreshTest {
    private lateinit var activity: Main
    private lateinit var button: ImageButton
    private lateinit var progress: ProgressBar

    @Before
    fun setup() {
        activity = Robolectric.buildActivity(Main::class.java).get()
        activity.setTheme(R.style.AppTheme_Default)
        activity.setContentView(R.layout.main)
        button = activity.findViewById(R.id.main_refresh_button)
        progress = activity.findViewById(R.id.main_refresh_progressBar)
        invoke("setupRefreshControl")
    }

    @Test
    fun ordinaryGenerationBlocksClicksAndPreservesViewOptions() {
        set("listGenerationRunning", true)
        set("isGridMode", true)
        activity.findViewById<EditText>(R.id.search_editText).setText("browser")
        queue("RUN", 8)
        assertControls(View.GONE, View.VISIBLE)
        val generation = (get("mListGeneration") as AtomicInteger).get()
        repeat(3) { button.performClick() }
        assertEquals(generation, (get("mListGeneration") as AtomicInteger).get())
        assertEquals("RUN", get("queuedFilter"))
        assertEquals(8, get("currentSortRule"))
        assertEquals(true, get("isGridMode"))
        assertEquals("browser", activity.findViewById<EditText>(R.id.search_editText).text.toString())
    }

    @Test
    fun drainedWorkerRestoresButtonForEachRunningFilter() {
        for (filter in listOf("RUN", "RUN_SYS", "RUN_USER")) {
            set("listGenerationRunning", true)
            queue(filter)
            assertControls(View.GONE, View.VISIBLE)
            set("listGenerationQueued", false)
            invoke("runQueuedListGenerations")
            assertFalse(get("listGenerationRunning") as Boolean)
            assertControls(View.VISIBLE, View.GONE)
        }
    }

    @Test
    fun failureRecoveryDoesNotUnlockButtonAheadOfPendingWork() {
        set("listGenerationRunning", true)
        queue("RUN")
        invoke("restoreListAfterFailedGeneration")
        assertControls(View.GONE, View.VISIBLE)
        assertTrue(get("listGenerationQueued") as Boolean)
        set("listGenerationQueued", false)
        invoke("runQueuedListGenerations")
        assertControls(View.VISIBLE, View.GONE)
    }

    @Test
    fun leavingRunningFilterHidesControlsAndInvalidatesOldResultImmediately() {
        set("listGenerationRunning", true)
        queue("RUN")
        val previous = (get("mListGeneration") as AtomicInteger).get()
        queue("all")
        assertTrue((get("mListGeneration") as AtomicInteger).get() > previous)
        assertControls(View.GONE, View.GONE)
        invoke("restoreListAfterFailedGeneration")
        set("listGenerationQueued", false)
        invoke("runQueuedListGenerations")
        assertControls(View.GONE, View.GONE)
    }

    @Test
    fun delayedControlUpdateUsesCurrentFilterRatherThanCapturedFilter() {
        set("currentFilter", "RUN")
        Thread { invoke("updateRefreshControlState") }.apply { start(); join() }
        set("currentFilter", "all")
        shadowOf(Looper.getMainLooper()).idle()
        assertControls(View.GONE, View.GONE)
    }

    @Test
    fun sortButtonVisibleOnlyForRunningFilters() {
        val sortButton = activity.findViewById<ImageButton>(R.id.main_sort_running_button)
        for (filter in listOf("RUN", "RUN_SYS", "RUN_USER")) {
            set("listGenerationRunning", true)
            queue(filter)
            set("listGenerationQueued", false)
            invoke("runQueuedListGenerations")
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(View.VISIBLE, sortButton.visibility)
            assertTrue(sortButton.isEnabled)
        }
        for (filter in listOf("all", "UF", "OF")) {
            set("listGenerationRunning", true)
            queue(filter)
            set("listGenerationQueued", false)
            invoke("runQueuedListGenerations")
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(View.GONE, sortButton.visibility)
        }
    }

    @Test
    fun restoreSortMethodStatusUsesActiveFilterWithoutCrossingSettings() {
        prefs().edit()
            .putBoolean("saveSortMethodStatus", true)
            .putInt("sortMethodStatus", 8)
            .putInt("runningSortMethodStatus", 12)
            .commit()
        set("currentFilter", "RUN")
        invoke("restoreSortMethodStatus")
        assertEquals(12, get("currentSortRule"))
        assertEquals(8, get("currentGeneralSortRule"))
        set("currentFilter", "all")
        invoke("restoreSortMethodStatus")
        assertEquals(8, get("currentSortRule"))
        assertEquals(12, get("currentRunningSortRule"))
    }

    @Test
    fun automaticRefreshKeepsUnsavedSortWhenSavingDisabled() {
        prefs().edit()
            .putBoolean("saveSortMethodStatus", false)
            .putInt("sortMethodStatus", 8)
            .putInt("runningSortMethodStatus", 12)
            .commit()
        set("listGenerationRunning", true)
        queue("RUN", 12)
        generate("RUN")
        assertEquals(12, get("queuedSortRule"))
        queue("all", 8)
        generate("all")
        assertEquals(8, get("queuedSortRule"))
    }

    @Test
    fun runningSortChangesDoNotOverwriteGeneralSetting() {
        prefs().edit()
            .putBoolean("saveSortMethodStatus", true)
            .putInt("sortMethodStatus", 8)
            .putInt("runningSortMethodStatus", 11)
            .commit()
        set("listGenerationRunning", true)
        queue("RUN", 12)
        assertEquals(12, prefs().getInt("runningSortMethodStatus", -1))
        assertEquals(8, prefs().getInt("sortMethodStatus", -1))
        queue("all", 12)
        assertEquals(8, get("queuedSortRule"))
        assertEquals(8, prefs().getInt("sortMethodStatus", -1))
        assertEquals(12, prefs().getInt("runningSortMethodStatus", -1))
    }

    private fun prefs(): SharedPreferences = PreferenceManager.getDefaultSharedPreferences(activity)

    private fun generate(filter: String) {
        Main::class.java.getDeclaredMethod("generateList", String::class.java)
            .apply { isAccessible = true }.invoke(activity, filter)
    }

    private fun queue(filter: String, sort: Int = 0) {
        Main::class.java.getDeclaredMethod("generateList", String::class.java, Int::class.javaPrimitiveType, String::class.java)
            .apply { isAccessible = true }.invoke(activity, filter, sort, null)
    }

    private fun invoke(name: String) {
        Main::class.java.getDeclaredMethod(name).apply { isAccessible = true }.invoke(activity)
    }

    private fun set(name: String, value: Any) {
        Main::class.java.getDeclaredField(name).apply { isAccessible = true }.set(activity, value)
    }

    private fun get(name: String): Any = Main::class.java.getDeclaredField(name)
        .apply { isAccessible = true }.get(activity)!!

    private fun assertControls(buttonVisibility: Int, progressVisibility: Int) {
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(buttonVisibility, button.visibility)
        assertEquals(progressVisibility, progress.visibility)
    }
}
