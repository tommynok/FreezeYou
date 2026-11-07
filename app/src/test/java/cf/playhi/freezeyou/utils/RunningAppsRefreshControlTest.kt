package cf.playhi.freezeyou.utils

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
}
