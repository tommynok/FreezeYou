package cf.playhi.freezeyou.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    private companion object {
        const val FLAG_SYSTEM = 0x00000001
        const val FLAG_INSTALLED = 0x00800000
    }
}
