package cf.playhi.freezeyou.viewmodel

import android.app.Application
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The ViewModel's bookkeeping runs inside withContext(NonCancellable): clearing the ViewModel
 * while a freeze commits must not drop the statistics and the result the activity waits for.
 * These tests used to hop to Dispatchers.Main from a coroutine on Dispatchers.IO while runBlocking
 * held the test thread; under Robolectric the Main looper is that blocked thread, so the hop could
 * never run and CI hung until the job was killed. The cancellation test now hops to
 * Dispatchers.IO instead, and both tests wait on their own job rather than on the shared scope.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class FreezeActivityViewModelScopeTest {

    @Test
    fun nonCancellableProtectsBookkeepingExecutionWhenParentIsCancelled() = runBlocking {
        val parentJob = Job()
        var bookkeepingCompleted = false
        var exceptionThrown = false

        val job = launch(parentJob) {
            // Cancel this coroutine itself, simulating onCleared mid-flight.
            cancel(CancellationException("ViewModel cleared / activity paused"))
            try {
                withContext(NonCancellable) {
                    // Simulates FreezeYouFUFSinglePackage bookkeeping block
                    bookkeepingCompleted = true
                }
            } catch (e: CancellationException) {
                exceptionThrown = true
            }
        }
        job.join()

        assertTrue("Bookkeeping must complete even when parent coroutine is cancelled", bookkeepingCompleted)
        assertTrue("No CancellationException must leak out of NonCancellable block", !exceptionThrown)
    }

    @Test
    fun ordinaryContextWithoutNonCancellableThrowsCancellationException() = runBlocking {
        val parentJob = Job()
        var bookkeepingCompleted = false
        var exceptionThrown = false

        val job = launch(parentJob) {
            cancel(CancellationException("ViewModel cleared"))
            try {
                // Without NonCancellable the cancelled job is checked before the block runs.
                withContext(Dispatchers.IO) {
                    bookkeepingCompleted = true
                }
            } catch (e: CancellationException) {
                exceptionThrown = true
            }
        }
        job.join()

        assertEquals("Without NonCancellable, bookkeeping must not have completed", false, bookkeepingCompleted)
        assertTrue("CancellationException must be thrown without NonCancellable", exceptionThrown)
    }
}
