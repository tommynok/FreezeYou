package cf.playhi.freezeyou.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UninstallPolicyTest {

    @Test
    fun fullRemovalOfUpdatedSystemAppsRequiresPrivilegedShell() {
        assertTrue(UninstallPolicyUtils.requiresPrivilegedShellForFullUninstall(true, true, false))
        assertFalse(UninstallPolicyUtils.requiresPrivilegedShellForFullUninstall(true, true, true))
        assertFalse(UninstallPolicyUtils.requiresPrivilegedShellForFullUninstall(false, false, false))
        assertFalse(UninstallPolicyUtils.requiresPrivilegedShellForFullUninstall(true, false, false))
    }

    @Test
    fun updatesOnlyIsAcceptedOnlyForAnUpdatedSystemApp() {
        assertTrue(UninstallPolicyUtils.isValidUpdatesOnlyRequest(true, true, true))
        assertFalse(UninstallPolicyUtils.isValidUpdatesOnlyRequest(false, true, true))
        assertFalse(UninstallPolicyUtils.isValidUpdatesOnlyRequest(true, false, true))
    }
}
