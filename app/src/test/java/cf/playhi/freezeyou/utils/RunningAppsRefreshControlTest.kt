package cf.playhi.freezeyou.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RunningAppsRefreshControlTest {
    @Test
    fun runningFiltersShowButtonOnlyWhileIdle() {
        for (filter in listOf("RUN", "RUN_SYS", "RUN_USER")) {
            assertTrue(RunningAppsRefreshControl.canTriggerRefresh(filter, false))
            assertTrue(RunningAppsRefreshControl.isButtonVisible(filter, false))
            assertFalse(RunningAppsRefreshControl.isProgressBarVisible(filter, false))
            assertFalse(RunningAppsRefreshControl.canTriggerRefresh(filter, true))
            assertFalse(RunningAppsRefreshControl.isButtonVisible(filter, true))
            assertTrue(RunningAppsRefreshControl.isProgressBarVisible(filter, true))
        }
    }

    @Test
    fun otherFiltersHideBothControlsEvenWhileWorkerIsBusy() {
        for (filter in listOf("all", "OF", "UF", "OO", "OOU", "FOQ", "APPLOCK", "OS", "OUS", "OU", "UFU", "", null)) {
            for (busy in listOf(false, true)) {
                assertFalse(RunningAppsRefreshControl.canTriggerRefresh(filter, busy))
                assertFalse(RunningAppsRefreshControl.isButtonVisible(filter, busy))
                assertFalse(RunningAppsRefreshControl.isProgressBarVisible(filter, busy))
            }
        }
    }

    @Test
    fun pruneRemovesStoppedPackagesAndKeepsRunningOnesInOrder() {
        val rows = mutableListOf<Map<String, Any?>>(
            mapOf("PackageName" to "com.running.a"),
            mapOf("PackageName" to "com.stopped.b"),
            mapOf("PackageName" to "com.running.c"),
            mapOf("PackageName" to "com.stopped.d")
        )
        val changed = RunningAppsRefreshControl.pruneStoppedRows(
            rows, setOf("com.running.a", "com.running.c"), "N/A")
        assertTrue(changed)
        assertEquals(listOf("com.running.a", "com.running.c"), rows.map { it["PackageName"] })
    }

    @Test
    fun pruneKeepsNotAvailableFooter() {
        val rows = mutableListOf<Map<String, Any?>>(
            mapOf("PackageName" to "com.stopped.a"),
            mapOf("PackageName" to "N/A", "Name" to "N/A")
        )
        val changed = RunningAppsRefreshControl.pruneStoppedRows(rows, setOf("com.other"), "N/A")
        assertTrue(changed)
        assertEquals(1, rows.size)
        assertEquals("N/A", rows[0]["PackageName"])
    }

    @Test
    fun pruneDoesNotWipeListWhenRunningSetEmpty() {
        val rows = mutableListOf<Map<String, Any?>>(
            mapOf("PackageName" to "com.a"),
            mapOf("PackageName" to "com.b")
        )
        val changed = RunningAppsRefreshControl.pruneStoppedRows(rows, emptySet(), "N/A")
        assertFalse(changed)
        assertEquals(2, rows.size)
    }

    @Test
    fun pruneHandlesNullRowsAndNullRunningSet() {
        assertFalse(RunningAppsRefreshControl.pruneStoppedRows(null, setOf("com.a"), "N/A"))
        val rows = mutableListOf<Map<String, Any?>>(mapOf("PackageName" to "com.a"))
        assertFalse(RunningAppsRefreshControl.pruneStoppedRows(rows, null, "N/A"))
        assertEquals(1, rows.size)
    }

    @Test
    fun pruneKeepsRowsWithoutStringPackageName() {
        val rows = mutableListOf<Map<String, Any?>>(
            mapOf("Name" to "no package"),
            mapOf("PackageName" to 42),
            mapOf("PackageName" to "com.stopped")
        )
        val changed = RunningAppsRefreshControl.pruneStoppedRows(rows, setOf("com.running"), "N/A")
        assertTrue(changed)
        assertEquals(2, rows.size)
    }

    @Test
    fun pruneReturnsFalseWhenNothingRemoved() {
        val rows = mutableListOf<Map<String, Any?>>(
            mapOf("PackageName" to "com.a"),
            mapOf("PackageName" to "com.b")
        )
        val changed = RunningAppsRefreshControl.pruneStoppedRows(rows, setOf("com.a", "com.b"), "N/A")
        assertFalse(changed)
        assertEquals(2, rows.size)
    }
}
