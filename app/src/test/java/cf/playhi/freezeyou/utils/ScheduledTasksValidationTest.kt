package cf.playhi.freezeyou.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduledTasksValidationTest {

    @Test
    fun `valid imported time tasks are accepted`() {
        assertTrue(TasksUtils.isValidImportedTimeTask(10, 30, "0", "ff,com.example", 1))
        assertTrue(TasksUtils.isValidImportedTimeTask(0, 0, "1234567", "fu,com.example", 0))
        assertTrue(TasksUtils.isValidImportedTimeTask(23, 59, "", "ff,com.example", 1))
        assertTrue(TasksUtils.isValidImportedTimeTask(12, 0, null, "ff,com.example", 1))
        assertTrue(TasksUtils.isValidImportedTimeTask(8, 15, "135", "ff,com.example", 1))
    }

    @Test
    fun `out of range hours and minutes are rejected`() {
        assertFalse(TasksUtils.isValidImportedTimeTask(-1, 30, "0", "ff,com.example", 1))
        assertFalse(TasksUtils.isValidImportedTimeTask(24, 30, "0", "ff,com.example", 1))
        assertFalse(TasksUtils.isValidImportedTimeTask(12, -1, "0", "ff,com.example", 1))
        assertFalse(TasksUtils.isValidImportedTimeTask(12, 60, "0", "ff,com.example", 1))
    }

    @Test
    fun `malformed repeat strings are rejected`() {
        assertFalse(TasksUtils.isValidImportedTimeTask(10, 30, "8", "ff,com.example", 1))
        assertFalse(TasksUtils.isValidImportedTimeTask(10, 30, "abc", "ff,com.example", 1))
        assertFalse(TasksUtils.isValidImportedTimeTask(10, 30, "1230", "ff,com.example", 1))
        assertFalse(TasksUtils.isValidImportedTimeTask(10, 30, "-1", "ff,com.example", 1))
    }

    @Test
    fun `empty or blank tasks and invalid enabled flags are rejected`() {
        assertFalse(TasksUtils.isValidImportedTimeTask(10, 30, "0", "", 1))
        assertFalse(TasksUtils.isValidImportedTimeTask(10, 30, "0", "   ", 1))
        assertFalse(TasksUtils.isValidImportedTimeTask(10, 30, "0", null, 1))
        assertFalse(TasksUtils.isValidImportedTimeTask(10, 30, "0", "ff,com.example", 2))
        assertFalse(TasksUtils.isValidImportedTimeTask(10, 30, "0", "ff,com.example", -1))
    }

    @Test
    fun `supported trigger tasks are accepted`() {
        val triggers = listOf(
            "onScreenOn",
            "onScreenOff",
            "onApplicationsForeground",
            "onLeaveApplications",
            "onUFApplications",
            "onFApplications"
        )
        for (trigger in triggers) {
            assertTrue(TasksUtils.isValidImportedTriggerTask(trigger, "okuf", 1))
            assertTrue(TasksUtils.isValidImportedTriggerTask(trigger, "ff,com.example", 0))
        }
    }

    @Test
    fun `unsupported triggers or invalid trigger fields are rejected`() {
        assertFalse(TasksUtils.isValidImportedTriggerTask("onUnknownTrigger", "okuf", 1))
        assertFalse(TasksUtils.isValidImportedTriggerTask("", "okuf", 1))
        assertFalse(TasksUtils.isValidImportedTriggerTask(null, "okuf", 1))
        assertFalse(TasksUtils.isValidImportedTriggerTask("onScreenOn", "", 1))
        assertFalse(TasksUtils.isValidImportedTriggerTask("onScreenOn", "   ", 1))
        assertFalse(TasksUtils.isValidImportedTriggerTask("onScreenOn", null, 1))
        assertFalse(TasksUtils.isValidImportedTriggerTask("onScreenOn", "okuf", 2))
    }

    @Test
    fun `delayTaskTriggerKeys includes timed and standalone tasks along with event triggers`() {
        val keys = setOf(
            "OSA_com.example.app",
            "OLA_com.example.app",
            "onScreenOn",
            "onScreenOff",
            "timedTask_12",
            TasksUtils.DELAYED_STANDALONE_TASKS,
            "unrelated_key",
            "publishedTimeTaskIds"
        )
        val triggers = TasksUtils.delayTaskTriggerKeys(keys)
        assertTrue(triggers.contains("OSA_com.example.app"))
        assertTrue(triggers.contains("OLA_com.example.app"))
        assertTrue(triggers.contains("onScreenOn"))
        assertTrue(triggers.contains("onScreenOff"))
        assertTrue(triggers.contains("timedTask_12"))
        assertTrue(triggers.contains(TasksUtils.DELAYED_STANDALONE_TASKS))
        assertFalse(triggers.contains("unrelated_key"))
        assertFalse(triggers.contains("publishedTimeTaskIds"))
    }
}
