package cf.playhi.freezeyou.viewmodel

import android.app.Application
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class FreezeActivityViewModelScopeTest {

    @Test
    fun nonCancellableProtectsBookkeepingExecutionWhenParentIsCancelled() = runBlocking {
        val parentJob = Job()
        var bookkeepingCompleted = false
        var exceptionThrown = false

        // Cancel the parent job prior to / during execution
        parentJob.cancel(CancellationException("ViewModel cleared / activity paused"))

        withContext(parentJob + Dispatchers.IO) {
            try {
                withContext(NonCancellable) {
                    // Simulates FreezeYouFUFSinglePackage bookkeeping block
                    withContext(Dispatchers.Main) {
                        bookkeepingCompleted = true
                    }
                }
            } catch (e: CancellationException) {
                exceptionThrown = true
            }
        }

        assertTrue("Bookkeeping must complete even when parent coroutine is cancelled", bookkeepingCompleted)
        assertTrue("No CancellationException must leak out of NonCancellable block", !exceptionThrown)
    }

    @Test
    fun ordinaryContextWithoutNonCancellableThrowsCancellationException() = runBlocking {
        val parentJob = Job()
        var bookkeepingCompleted = false
        var exceptionThrown = false

        parentJob.cancel(CancellationException("ViewModel cleared"))

        withContext(parentJob + Dispatchers.IO) {
            try {
                // Without NonCancellable, hopping to Main throws CancellationException
                withContext(Dispatchers.Main) {
                    bookkeepingCompleted = true
                }
            } catch (e: CancellationException) {
                exceptionThrown = true
            }
        }

        assertEquals("Without NonCancellable, bookkeeping must not have completed", false, bookkeepingCompleted)
        assertTrue("CancellationException must be thrown without NonCancellable", exceptionThrown)
    }
}
