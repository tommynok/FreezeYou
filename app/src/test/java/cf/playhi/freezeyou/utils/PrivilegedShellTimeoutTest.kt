package cf.playhi.freezeyou.utils

import cf.playhi.freezeyou.service.InstallPackagesService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivilegedShellTimeoutTest {

    @Test
    fun defaultCommandTimeoutIsOneMinute() {
        assertEquals(60_000L, PrivilegedShellUtils.DEFAULT_COMMAND_TIMEOUT_MS)
    }

    @Test
    fun installTimeoutIsTwoMinutes() {
        assertEquals(120_000L, InstallPackagesService.INSTALL_TIMEOUT_MS)
    }

    @Test
    fun uninstallTimeoutIsOneMinute() {
        assertEquals(60_000L, InstallPackagesService.UNINSTALL_TIMEOUT_MS)
    }

    @Test
    fun shellSuccessRequiresZeroExitAndNoFailureOutput() {
        assertTrue(PrivilegedShellUtils.isSuccessful(0, "Success"))
        assertTrue(PrivilegedShellUtils.isSuccessful(0, null))
        assertTrue(PrivilegedShellUtils.isSuccessful(0, ""))
        assertFalse(PrivilegedShellUtils.isSuccessful(1, "Success"))
        assertFalse(PrivilegedShellUtils.isSuccessful(0, "Failure [not installed for user 0]"))
        assertFalse(PrivilegedShellUtils.isSuccessful(0, "Error: unknown package"))
        assertFalse(PrivilegedShellUtils.isSuccessful(0, "Exception occurred"))
        assertFalse(PrivilegedShellUtils.isSuccessful(0, "Failed to install"))
        assertFalse(PrivilegedShellUtils.isSuccessful(0, "Unknown command: pm"))
    }
}
