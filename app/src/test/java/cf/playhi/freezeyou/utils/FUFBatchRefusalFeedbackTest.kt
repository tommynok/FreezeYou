package cf.playhi.freezeyou.utils

import cf.playhi.freezeyou.fuf.FUFSinglePackage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * An in-process batch used to fold every non-success result into one failed list, so an application
 * the user's own guards refused was named in the generic "N applications failed" toast. The two
 * guard codes now travel in lists of their own and get the specific toast the single-package path
 * shows. The loop itself sits behind MMKV, notifications and Android toasts, so the split is kept
 * in a pure helper and held by these tests.
 */
class FUFBatchRefusalFeedbackTest {

    private val succeeded = mutableListOf<String>()
    private val stillNotifying = mutableListOf<String>()
    private val foreground = mutableListOf<String>()
    private val failed = mutableListOf<String>()

    private fun classify(result: Int, pkgName: String) {
        FUFUtils.categorizeBatchResult(
            result, pkgName, succeeded, stillNotifying, foreground, failed
        )
    }

    @Test
    fun `both success codes count as succeeded`() {
        classify(FUFSinglePackage.ERROR_NO_ERROR_SUCCESS, "com.example.ok")
        classify(FUFSinglePackage.ERROR_NO_ERROR_CAUGHT_UNKNOWN_RESULT, "com.example.unknown")

        assertEquals(listOf("com.example.ok", "com.example.unknown"), succeeded)
        assertTrue(stillNotifying.isEmpty())
        assertTrue(foreground.isEmpty())
        assertTrue(failed.isEmpty())
    }

    @Test
    fun `a still notifying refusal has its own list, not the failure one`() {
        classify(
            FUFSinglePackage.ERROR_USER_SET_NOT_ALLOWED_TO_FREEZE_NOTIFYING_APPLICATION,
            "com.example.notifying"
        )

        assertEquals(listOf("com.example.notifying"), stillNotifying)
        assertTrue(succeeded.isEmpty())
        assertTrue(foreground.isEmpty())
        assertTrue(failed.isEmpty())
    }

    @Test
    fun `a foreground refusal has its own list, not the failure one`() {
        classify(
            FUFSinglePackage.ERROR_USER_SET_NOT_ALLOWED_TO_FREEZE_FOREGROUND_APPLICATION,
            "com.example.foreground"
        )

        assertEquals(listOf("com.example.foreground"), foreground)
        assertTrue(succeeded.isEmpty())
        assertTrue(stillNotifying.isEmpty())
        assertTrue(failed.isEmpty())
    }

    @Test
    fun `every other error stays a failure`() {
        classify(FUFSinglePackage.ERROR_OTHER, "com.example.other")
        classify(FUFSinglePackage.ERROR_NO_ROOT_PERMISSION, "com.example.root")
        classify(FUFSinglePackage.ERROR_STATE_DID_NOT_CHANGE, "com.example.unchanged")

        assertEquals(
            listOf("com.example.other", "com.example.root", "com.example.unchanged"),
            failed
        )
        assertTrue(succeeded.isEmpty())
        assertTrue(stillNotifying.isEmpty())
        assertTrue(foreground.isEmpty())
    }

    @Test
    fun `a mixed batch is split into the four lists in the order it was walked`() {
        classify(FUFSinglePackage.ERROR_NO_ERROR_SUCCESS, "com.example.frozen")
        classify(
            FUFSinglePackage.ERROR_USER_SET_NOT_ALLOWED_TO_FREEZE_NOTIFYING_APPLICATION,
            "com.example.notifying"
        )
        classify(
            FUFSinglePackage.ERROR_USER_SET_NOT_ALLOWED_TO_FREEZE_FOREGROUND_APPLICATION,
            "com.example.foreground"
        )
        classify(FUFSinglePackage.ERROR_OTHER, "com.example.failed")
        classify(FUFSinglePackage.ERROR_NO_ERROR_CAUGHT_UNKNOWN_RESULT, "com.example.unknown")

        assertEquals(listOf("com.example.frozen", "com.example.unknown"), succeeded)
        assertEquals(listOf("com.example.notifying"), stillNotifying)
        assertEquals(listOf("com.example.foreground"), foreground)
        assertEquals(listOf("com.example.failed"), failed)
    }
}
