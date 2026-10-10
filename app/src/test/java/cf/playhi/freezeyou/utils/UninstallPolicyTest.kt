package cf.playhi.freezeyou.utils

import org.junit.Assert.assertEquals
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
    fun rootIsNotProbedWhenDeviceOwnerCanHandleTheOperation() {
        assertFalse(UninstallPolicyUtils.shouldCheckRootPermission(true, false))
        assertTrue(UninstallPolicyUtils.shouldCheckRootPermission(true, true))
        assertTrue(UninstallPolicyUtils.shouldCheckRootPermission(false, false))
    }

    @Test
    fun updatesOnlyIsAcceptedOnlyForAnUpdatedSystemApp() {
        assertTrue(UninstallPolicyUtils.isValidUpdatesOnlyRequest(true, true, true))
        assertFalse(UninstallPolicyUtils.isValidUpdatesOnlyRequest(false, true, true))
        assertFalse(UninstallPolicyUtils.isValidUpdatesOnlyRequest(true, false, true))
    }

    @Test
    fun shellRemovalOfOrdinaryAndFactorySystemAppsIsScopedToCurrentUser() {
        val expected = listOf("pm uninstall --user 10 'com.example.app'")

        assertEquals(
            expected,
            UninstallPolicyUtils.buildUninstallCommands(false, false, false, 10, "com.example.app")
        )
        assertEquals(
            expected,
            UninstallPolicyUtils.buildUninstallCommands(true, false, false, 10, "com.example.app")
        )
    }

    @Test
    fun updatedSystemAppFullRemovalRevertsSharedUpdateThenRemovesForCurrentUser() {
        assertEquals(
            listOf(
                "pm uninstall 'com.example.app'",
                "pm uninstall --user 10 'com.example.app'"
            ),
            UninstallPolicyUtils.buildUninstallCommands(true, true, false, 10, "com.example.app")
        )
    }

    @Test
    fun updatesOnlyRequestRevertsSharedUpdateWithoutRemovingPackageForUser() {
        assertEquals(
            listOf("pm uninstall 'com.example.app'"),
            UninstallPolicyUtils.buildUninstallCommands(true, true, true, 10, "com.example.app")
        )
    }

    @Test
    fun uninstallCommandEscapesShellMetacharacters() {
        assertEquals(
            listOf("pm uninstall --user 10 'com.example.quoted'\\''app'"),
            UninstallPolicyUtils.buildUninstallCommands(
                false, false, false, 10, "com.example.quoted'app")
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun updatesOnlyRequestForUserAppIsRejected() {
        UninstallPolicyUtils.buildUninstallCommands(false, false, true, 10, "com.example.app")
    }
}
