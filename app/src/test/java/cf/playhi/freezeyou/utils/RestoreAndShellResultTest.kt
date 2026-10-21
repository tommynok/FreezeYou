package cf.playhi.freezeyou.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RestoreAndShellResultTest {

    @Test
    fun onlyUninstalledSystemAppsAreRestorable() {
        val uninstalledSystemApp = FLAG_SYSTEM
        val installedSystemApp = FLAG_SYSTEM or FLAG_INSTALLED
        val uninstalledUserApp = 0

        assertTrue(RestoreUtils.isRestorableSystemAppFlags(uninstalledSystemApp))
        assertFalse(RestoreUtils.isRestorableSystemAppFlags(installedSystemApp))
        assertFalse(RestoreUtils.isRestorableSystemAppFlags(uninstalledUserApp))
    }

    @Test
    fun restoreActionIsRestrictedToUninstalledSystemFilter() {
        assertTrue(RestoreUtils.isRestoreFilter("OUS"))
        assertFalse(RestoreUtils.isRestoreFilter("all"))
        assertFalse(RestoreUtils.isRestoreFilter(null))
    }

    @Test
    fun androidUidMapsToItsOwningUser() {
        assertEquals(0, AndroidUserUtils.userIdFromUid(12345))
        assertEquals(10, AndroidUserUtils.userIdFromUid(1012345))
    }

    @Test
    fun shellSuccessRequiresZeroExitAndNoFailureOutput() {
        assertTrue(PrivilegedShellUtils.isSuccessful(0, "Success"))
        assertTrue(PrivilegedShellUtils.isSuccessful(0, "Package restored for user: 0"))
        assertFalse(PrivilegedShellUtils.isSuccessful(1, "Success"))
        assertFalse(PrivilegedShellUtils.isSuccessful(0, "Failure [not installed for user 0]"))
        assertFalse(PrivilegedShellUtils.isSuccessful(0, "Error: unknown package"))
    }

    @Test
    fun batchLineAsksTheShellForTheExitCodeOfThatOneCommand() {
        assertEquals(
            "(pm disable 'com.example.one') 2>&1; echo __FREEZEYOU_RC__3:\$?\n",
            PrivilegedShellUtils.batchLine("pm disable 'com.example.one'", 3)
        )
    }

    @Test
    fun aCommandSpanningLinesIsRefusedRatherThanAnsweredWrongly() {
        // Its marker would follow only the first line, and the exit code reported belongs to the
        // last, so the batch would credit commands that never ran.
        assertThrows(IllegalArgumentException::class.java) {
            PrivilegedShellUtils.batchLine("pm disable com.a\npm enable com.b", 0)
        }
    }

    @Test
    fun batchOutputIsSplitBackIntoOneResultPerCommand() {
        val results = readBatch(
            """
            Package com.a new state: disabled-user
            __FREEZEYOU_RC__0:0
            Failure [Package com.b is not installed]
            __FREEZEYOU_RC__1:1
            Package com.c new state: enabled
            __FREEZEYOU_RC__2:0
            """.trimIndent()
        )
        assertEquals(3, results.size)
        assertEquals(0, results[0]!!.exitCode)
        assertEquals("Package com.a new state: disabled-user", results[0]!!.output)
        // The failure of the second package is the second package's, not the batch's.
        assertEquals(1, results[1]!!.exitCode)
        assertFalse(PrivilegedShellUtils.isSuccessful(results[1]!!.exitCode, results[1]!!.output))
        assertEquals("Package com.c new state: enabled", results[2]!!.output)
    }

    @Test
    fun aCommandTheSessionNeverAnsweredIsNotCountedAsSuccess() {
        // The session died mid-batch: the text is there, the answer is not.
        val results = readBatch(
            "Package com.a new state: disabled\n__FREEZEYOU_RC__0:0\nWarning: com.b is being killed\n"
        )
        assertEquals(1, results.size)
        assertNull(results[1])
    }

    @Test
    fun aLineThatOnlyLooksLikeAMarkerStaysCommandOutput() {
        val results = readBatch("__FREEZEYOU_RC__x:0\n__FREEZEYOU_RC__0:0\n")
        assertEquals(1, results.size)
        assertEquals("__FREEZEYOU_RC__x:0", results[0]!!.output)
    }

    private fun readBatch(output: String): Map<Int, PrivilegedShellUtils.CommandResult> =
        PrivilegedShellUtils.parseBatchResults(
            java.io.ByteArrayInputStream(output.toByteArray())
        )

    private companion object {
        const val FLAG_SYSTEM = 0x00000001
        const val FLAG_INSTALLED = 0x00800000
    }
}
