package cf.playhi.freezeyou.fuf

import android.app.Application
import android.content.pm.PackageManager
import cf.playhi.freezeyou.fuf.FUFSinglePackage.Companion.API_FREEZEYOU_SHIZUKU_SYSTEM_APP_ENABLE_DISABLE
import cf.playhi.freezeyou.fuf.FUFSinglePackage.Companion.API_FREEZEYOU_SHIZUKU_SYSTEM_APP_ENABLE_DISABLE_UNTIL_USED
import cf.playhi.freezeyou.fuf.FUFSinglePackage.Companion.API_FREEZEYOU_SHIZUKU_SYSTEM_APP_ENABLE_DISABLE_USER
import cf.playhi.freezeyou.fuf.FUFSinglePackage.Companion.ERROR_NO_ERROR_SUCCESS
import cf.playhi.freezeyou.fuf.FUFSinglePackage.Companion.ERROR_NO_ROOT_PERMISSION
import cf.playhi.freezeyou.fuf.FUFSinglePackage.Companion.ERROR_OTHER
import cf.playhi.freezeyou.fuf.FUFSinglePackage.Companion.classifyRootResult
import cf.playhi.freezeyou.fuf.FUFSinglePackage.Companion.resolveFreezeState
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class FUFSinglePackageResultTest {

    @Test
    fun rootSuccessRequiresZeroExitAndNoFailureOutput() {
        assertEquals(ERROR_NO_ERROR_SUCCESS, classifyRootResult(0, ""))
        assertEquals(ERROR_NO_ERROR_SUCCESS, classifyRootResult(0, null))
        assertEquals(
            ERROR_NO_ERROR_SUCCESS,
            classifyRootResult(0, "Package com.example new state: disabled-user")
        )
    }

    @Test
    fun rootExitZeroWithErrorMessageIsClassifiedAsErrorOther() {
        assertEquals(
            ERROR_OTHER,
            classifyRootResult(0, "Error: java.lang.SecurityException")
        )
        assertEquals(
            ERROR_OTHER,
            classifyRootResult(0, "Failure [not installed for user 0]")
        )
    }

    @Test
    fun rootNonZeroExitWithEmptyOutputIndicatesDeniedOrMissingRoot() {
        assertEquals(ERROR_NO_ROOT_PERMISSION, classifyRootResult(1, ""))
        assertEquals(ERROR_NO_ROOT_PERMISSION, classifyRootResult(1, "   "))
        assertEquals(ERROR_NO_ROOT_PERMISSION, classifyRootResult(1, null))
    }

    @Test
    fun rootNonZeroExitWithPermissionDeniedIndicatesRootDenied() {
        assertEquals(
            ERROR_NO_ROOT_PERMISSION,
            classifyRootResult(1, "Permission denied")
        )
        assertEquals(
            ERROR_NO_ROOT_PERMISSION,
            classifyRootResult(1, "/system/bin/sh: su: permission denied")
        )
    }

    @Test
    fun rootNonZeroExitWithPackageErrorRemainsErrorOther() {
        assertEquals(
            ERROR_OTHER,
            classifyRootResult(1, "Error: package not found")
        )
        assertEquals(
            ERROR_OTHER,
            classifyRootResult(1, "Error: java.lang.IllegalArgumentException")
        )
    }

    @Test
    fun shizukuUnderSuiPreservesAllUpstreamModes() {
        assertEquals(
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED,
            resolveFreezeState(API_FREEZEYOU_SHIZUKU_SYSTEM_APP_ENABLE_DISABLE_UNTIL_USED, 0)
        )
        assertEquals(
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,
            resolveFreezeState(API_FREEZEYOU_SHIZUKU_SYSTEM_APP_ENABLE_DISABLE_USER, 0)
        )
        assertEquals(
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            resolveFreezeState(API_FREEZEYOU_SHIZUKU_SYSTEM_APP_ENABLE_DISABLE, 0)
        )
    }

    @Test
    fun shizukuUnderPlainAdbRestrictsAllModesToDisabledUser() {
        assertEquals(
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,
            resolveFreezeState(API_FREEZEYOU_SHIZUKU_SYSTEM_APP_ENABLE_DISABLE_UNTIL_USED, 2000)
        )
        assertEquals(
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,
            resolveFreezeState(API_FREEZEYOU_SHIZUKU_SYSTEM_APP_ENABLE_DISABLE_USER, 2000)
        )
        assertEquals(
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,
            resolveFreezeState(API_FREEZEYOU_SHIZUKU_SYSTEM_APP_ENABLE_DISABLE, 2000)
        )
    }
}
