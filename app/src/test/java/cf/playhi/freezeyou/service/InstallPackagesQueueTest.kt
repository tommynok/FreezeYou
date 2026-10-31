package cf.playhi.freezeyou.service

import android.app.Application
import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * One install or uninstall at a time. The queue used to be a plain ArrayList and a boolean shared
 * between the main thread and a worker: a second request could start a second worker, and the
 * first worker to finish could stop the service while another request was still pending. The
 * queue now owns its monitor, and taking the last request is atomic with the drain decision.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class InstallPackagesQueueTest {

    private fun newQueue() = InstallPackagesService.InstallRequestQueue()

    @Test
    fun `a burst of requests starts one worker and keeps the order`() {
        val queue = newQueue()

        assertTrue(queue.enqueue(Intent("first"), 1))
        assertFalse(queue.enqueue(Intent("second"), 2))
        assertFalse(queue.enqueue(Intent("third"), 3))

        assertEquals("first", queue.takeNextOrNull()?.action)
        assertEquals("second", queue.takeNextOrNull()?.action)
        assertEquals("third", queue.takeNextOrNull()?.action)
        assertNull(queue.takeNextOrNull())
        assertEquals(3, queue.markDrainedIfEmpty())
    }

    @Test
    fun `the worker is told to stop only after the last request`() {
        val queue = newQueue()

        assertTrue(queue.enqueue(Intent("first"), 7))
        assertFalse(queue.enqueue(Intent("second"), 8))
        assertEquals("first", queue.takeNextOrNull()?.action)
        assertEquals(-1, queue.markDrainedIfEmpty())

        assertEquals("second", queue.takeNextOrNull()?.action)
        assertEquals(8, queue.markDrainedIfEmpty())
    }

    @Test
    fun `a request queued between taking and draining is not lost`() {
        val queue = newQueue()

        assertTrue(queue.enqueue(Intent("first"), 1))
        assertEquals("first", queue.takeNextOrNull()?.action)

        // The main thread queues the second request while the worker is between tasks.
        assertFalse(queue.enqueue(Intent("second"), 2))
        assertEquals(-1, queue.markDrainedIfEmpty())

        assertEquals("second", queue.takeNextOrNull()?.action)
        assertEquals(2, queue.markDrainedIfEmpty())
    }

    @Test
    fun `a request after a drain starts a new worker`() {
        val queue = newQueue()

        assertTrue(queue.enqueue(Intent("first"), 1))
        assertEquals("first", queue.takeNextOrNull()?.action)
        assertEquals(1, queue.markDrainedIfEmpty())

        assertTrue(queue.enqueue(Intent("second"), 2))
        assertEquals("second", queue.takeNextOrNull()?.action)
        assertEquals(2, queue.markDrainedIfEmpty())
    }

    @Test
    fun `an idle stop carries the latest start id and a busy queue is not stopped`() {
        val queue = newQueue()

        assertEquals(5, queue.idleStopId(5))

        assertTrue(queue.enqueue(Intent("install"), 6))
        // A waiting-for-leaving command arrives while the worker is busy.
        assertEquals(-1, queue.idleStopId(7))

        assertEquals("install", queue.takeNextOrNull()?.action)
        assertEquals(7, queue.markDrainedIfEmpty())
    }

    @Test
    fun `concurrent requests start a single worker and every request survives`() {
        val queue = newQueue()
        val requestCount = 8
        val ready = CountDownLatch(requestCount)
        val start = CountDownLatch(1)
        val finished = CountDownLatch(requestCount)
        val workers = AtomicInteger()

        repeat(requestCount) { index ->
            Thread {
                ready.countDown()
                start.await()
                if (queue.enqueue(Intent("request-$index"), index + 1)) {
                    workers.incrementAndGet()
                }
                finished.countDown()
            }.start()
        }

        assertTrue(ready.await(5, TimeUnit.SECONDS))
        start.countDown()
        assertTrue(finished.await(5, TimeUnit.SECONDS))
        assertEquals(1, workers.get())

        val actions = HashSet<String>()
        while (true) {
            val next = queue.takeNextOrNull() ?: break
            actions.add(next.action!!)
        }
        assertEquals(requestCount, actions.size)
        assertTrue(queue.markDrainedIfEmpty() >= 0)
    }
}
