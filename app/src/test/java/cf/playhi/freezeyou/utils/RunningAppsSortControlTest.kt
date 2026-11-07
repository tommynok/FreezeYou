package cf.playhi.freezeyou.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.Collator
import java.util.Locale

class RunningAppsSortControlTest {

    @Test
    fun runningFiltersIdentifyCorrectly() {
        for (filter in listOf("RUN", "RUN_SYS", "RUN_USER")) {
            assertTrue(RunningAppsSortControl.isRunningFilter(filter))
            assertTrue(RunningAppsSortControl.isSortButtonVisible(filter))
        }
        for (filter in listOf("all", "OF", "UF", "OO", "OOU", "FOQ", "APPLOCK", "OS", "OUS", "OU", "UFU", "", null)) {
            assertFalse(RunningAppsSortControl.isRunningFilter(filter))
            assertFalse(RunningAppsSortControl.isSortButtonVisible(filter))
        }
    }

    @Test
    fun runningSortRulesIdentifyCorrectly() {
        assertTrue(RunningAppsSortControl.isRunningSortRule(RunningAppsSortControl.SORT_BY_RUNNING_RAM))
        assertTrue(RunningAppsSortControl.isRunningSortRule(RunningAppsSortControl.SORT_BY_RUNNING_CPU))
        assertFalse(RunningAppsSortControl.isRunningSortRule(RunningAppsSortControl.SORT_BY_DEFAULT))
        assertFalse(RunningAppsSortControl.isRunningSortRule(RunningAppsSortControl.SORT_BY_ALPHABETICAL))
        assertFalse(RunningAppsSortControl.isRunningSortRule(1))
    }

    @Test
    fun resolveSortRuleDistinguishesRunningAndGeneralFilters() {
        val runningRule = RunningAppsSortControl.SORT_BY_RUNNING_RAM
        val generalRule = RunningAppsSortControl.SORT_BY_ALPHABETICAL

        assertEquals(runningRule, RunningAppsSortControl.resolveSortRuleForFilter("RUN", generalRule, runningRule))
        assertEquals(runningRule, RunningAppsSortControl.resolveSortRuleForFilter("RUN_SYS", generalRule, runningRule))
        assertEquals(runningRule, RunningAppsSortControl.resolveSortRuleForFilter("RUN_USER", generalRule, runningRule))

        assertEquals(generalRule, RunningAppsSortControl.resolveSortRuleForFilter("all", generalRule, runningRule))
        assertEquals(generalRule, RunningAppsSortControl.resolveSortRuleForFilter("UF", generalRule, runningRule))
        assertEquals(generalRule, RunningAppsSortControl.resolveSortRuleForFilter("FOQ", generalRule, runningRule))
    }

    @Test
    fun ramComparatorOrdersHighestMemoryFirstAndHandlesNullsAndTies() {
        val collator = Collator.getInstance(Locale.US)
        val comparator = RunningAppsSortControl.createRamComparator(collator)

        val appHighRam = mapOf<String, Any?>(
            "Name" to "Alpha",
            "PackageName" to "com.app.alpha",
            "RunningUsage" to RunningAppsUtils.ResourceUsage(5.0, 500_000L)
        )
        val appLowRam = mapOf<String, Any?>(
            "Name" to "Beta",
            "PackageName" to "com.app.beta",
            "RunningUsage" to RunningAppsUtils.ResourceUsage(20.0, 100_000L)
        )
        val appEqualRamHigherCpu = mapOf<String, Any?>(
            "Name" to "Gamma",
            "PackageName" to "com.app.gamma",
            "RunningUsage" to RunningAppsUtils.ResourceUsage(50.0, 500_000L)
        )
        val appNoUsage = mapOf<String, Any?>(
            "Name" to "Zeta",
            "PackageName" to "com.app.zeta"
        )

        val list = mutableListOf(appNoUsage, appLowRam, appHighRam, appEqualRamHigherCpu)
        list.sortWith(comparator)

        // Expected order:
        // 1. appEqualRamHigherCpu (500_000L RAM, 50.0 CPU)
        // 2. appHighRam (500_000L RAM, 5.0 CPU)
        // 3. appLowRam (100_000L RAM)
        // 4. appNoUsage (null RAM)
        assertEquals("com.app.gamma", list[0]["PackageName"])
        assertEquals("com.app.alpha", list[1]["PackageName"])
        assertEquals("com.app.beta", list[2]["PackageName"])
        assertEquals("com.app.zeta", list[3]["PackageName"])
    }

    @Test
    fun cpuComparatorOrdersHighestCpuFirstAndHandlesNullsAndTies() {
        val collator = Collator.getInstance(Locale.US)
        val comparator = RunningAppsSortControl.createCpuComparator(collator)

        val appHighCpu = mapOf<String, Any?>(
            "Name" to "Alpha",
            "PackageName" to "com.app.alpha",
            "RunningUsage" to RunningAppsUtils.ResourceUsage(85.0, 100_000L)
        )
        val appLowCpu = mapOf<String, Any?>(
            "Name" to "Beta",
            "PackageName" to "com.app.beta",
            "RunningUsage" to RunningAppsUtils.ResourceUsage(10.0, 800_000L)
        )
        val appEqualCpuHigherRam = mapOf<String, Any?>(
            "Name" to "Gamma",
            "PackageName" to "com.app.gamma",
            "RunningUsage" to RunningAppsUtils.ResourceUsage(85.0, 500_000L)
        )
        val appNoUsage = mapOf<String, Any?>(
            "Name" to "Zeta",
            "PackageName" to "com.app.zeta"
        )

        val list = mutableListOf(appNoUsage, appLowCpu, appHighCpu, appEqualCpuHigherRam)
        list.sortWith(comparator)

        // Expected order:
        // 1. appEqualCpuHigherRam (85.0 CPU, 500_000L RAM)
        // 2. appHighCpu (85.0 CPU, 100_000L RAM)
        // 3. appLowCpu (10.0 CPU)
        // 4. appNoUsage (null CPU)
        assertEquals("com.app.gamma", list[0]["PackageName"])
        assertEquals("com.app.alpha", list[1]["PackageName"])
        assertEquals("com.app.beta", list[2]["PackageName"])
        assertEquals("com.app.zeta", list[3]["PackageName"])
    }
}
