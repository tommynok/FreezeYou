package cf.playhi.freezeyou.utils

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ForceStopResultTest {

    @Test
    fun buildCommandsQuotesPackageNames() {
        val targets = listOf("com.example.one", "com.example.two'injection")
        val commands = ForceStopUtils.buildForceStopCommands(targets)
        assertEquals(2, commands.size)
        assertEquals("am force-stop 'com.example.one'", commands[0])
        assertEquals("am force-stop 'com.example.two'\\''injection'", commands[1])
    }

    @Test
    fun buildCommandsHandlesNullAndEmpty() {
        assertTrue(ForceStopUtils.buildForceStopCommands(null).isEmpty())
        assertTrue(ForceStopUtils.buildForceStopCommands(emptyList()).isEmpty())
    }

    @Test
    fun resolveOutcomesWhenSessionNotStartedTreatsAllTargetsAsFailed() {
        val targets = listOf("com.a", "com.b")
        val outcomes = ForceStopUtils.resolveOutcomes(targets, false, emptyList())
        assertEquals(0, outcomes.succeeded.size)
        assertEquals(2, outcomes.failed.size)
        assertEquals("com.a", outcomes.failed[0])
        assertEquals("com.b", outcomes.failed[1])
    }

    @Test
    fun resolveOutcomesPartitioningSuccessAndFailure() {
        val targets = listOf("com.a", "com.b", "com.c")
        val commands = ForceStopUtils.buildForceStopCommands(targets)
        val reported = mapOf(
            0 to PrivilegedShellUtils.CommandResult(0, ""),
            1 to PrivilegedShellUtils.CommandResult(1, "Error: package not found"),
            2 to PrivilegedShellUtils.CommandResult(0, "Error: java.lang.SecurityException")
        )
        val cmdOutcomes = PrivilegedShellUtils.toOutcomes(commands, 0, reported)
        val outcomes = ForceStopUtils.resolveOutcomes(targets, true, cmdOutcomes)

        assertEquals(listOf("com.a"), outcomes.succeeded)
        assertEquals(listOf("com.b", "com.c"), outcomes.failed)
    }

    @Test
    fun resolveOutcomesHandlesUnreportedCommandAsFailed() {
        val targets = listOf("com.a", "com.b")
        val commands = ForceStopUtils.buildForceStopCommands(targets)
        val reported = mapOf(
            0 to PrivilegedShellUtils.CommandResult(0, "")
        )
        val cmdOutcomes = PrivilegedShellUtils.toOutcomes(commands, 0, reported)
        val outcomes = ForceStopUtils.resolveOutcomes(targets, true, cmdOutcomes)

        assertEquals(listOf("com.a"), outcomes.succeeded)
        assertEquals(listOf("com.b"), outcomes.failed)
    }
}
