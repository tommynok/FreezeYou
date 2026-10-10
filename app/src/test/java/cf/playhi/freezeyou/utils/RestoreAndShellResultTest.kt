package cf.playhi.freezeyou.utils

import android.content.pm.ApplicationInfo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RestoreAndShellResultTest {

    @Test
    fun onlyUninstalledSystemAppsAreRestorable() {
        val uninstalledSystemApp = ApplicationInfo().apply {
            flags = ApplicationInfo.FLAG_SYSTEM
        }
        val installedSystemApp = ApplicationInfo().apply {
            flags = ApplicationInfo.FLAG_SYSTEM or FLAG_INSTALLED
        }
        val uninstalledUserApp = ApplicationInfo().apply {
            flags = 0
        }

        assertTrue(RestoreUtils.isRestorableSystemApp(uninstalledSystemApp))
        assertFalse(RestoreUtils.isRestorableSystemApp(installedSystemApp))
        assertFalse(RestoreUtils.isRestorableSystemApp(uninstalledUserApp))
        assertFalse(RestoreUtils.areAllRestorableSystemApps(emptyList()))
        assertTrue(RestoreUtils.areAllRestorableSystemApps(listOf(uninstalledSystemApp)))
        assertFalse(
            RestoreUtils.areAllRestorableSystemApps(
                listOf(uninstalledSystemApp, installedSystemApp)
            )
        )
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
        const val FLAG_INSTALLED = 0x00800000
    }
}
