package cf.playhi.freezeyou.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class RunningAppsUsageCacheTest {
    @Test
    fun freshPayloadReturnedWithinWindow() {
        val cache = RunningAppsUsageCache<String>()
        cache.store("scan", 1_000L, cache.generation())
        assertEquals("scan", cache.freshPayload(1_000L, 15_000L))
        assertEquals("scan", cache.freshPayload(10_000L, 15_000L))
    }

    @Test
    fun ageBoundaryIsInclusive() {
        val cache = RunningAppsUsageCache<String>()
        cache.store("scan", 1_000L, cache.generation())
        assertEquals("scan", cache.freshPayload(16_000L, 15_000L))
        assertNull(cache.freshPayload(16_001L, 15_000L))
    }

    @Test
    fun expiredPayloadIsNotReturned() {
        val cache = RunningAppsUsageCache<String>()
        cache.store("scan", 1_000L, cache.generation())
        assertNull(cache.freshPayload(60_000L, 15_000L))
    }

    @Test
    fun clockRollbackCountsAsStale() {
        val cache = RunningAppsUsageCache<String>()
        cache.store("scan", 10_000L, cache.generation())
        assertNull(cache.freshPayload(9_999L, 15_000L))
    }

    @Test
    fun emptyCacheReturnsNull() {
        val cache = RunningAppsUsageCache<String>()
        assertNull(cache.freshPayload(1_000L, 15_000L))
    }

    @Test
    fun clearRemovesPayload() {
        val cache = RunningAppsUsageCache<String>()
        cache.store("scan", 1_000L, cache.generation())
        cache.clear()
        assertNull(cache.freshPayload(1_000L, 15_000L))
    }

    @Test
    fun storeNullIsIgnored() {
        val cache = RunningAppsUsageCache<String>()
        cache.store("scan", 1_000L, cache.generation())
        cache.store(null, 2_000L, cache.generation())
        assertEquals("scan", cache.freshPayload(2_000L, 15_000L))
    }

    @Test
    fun storeOverwritesPreviousSnapshotAndTimestamp() {
        val cache = RunningAppsUsageCache<String>()
        cache.store("old", 1_000L, cache.generation())
        cache.store("new", 20_000L, cache.generation())
        assertEquals("new", cache.freshPayload(20_000L, 15_000L))
        assertNull(cache.freshPayload(40_000L, 15_000L))
    }

    @Test
    fun freshPayloadReturnsStoredInstance() {
        val cache = RunningAppsUsageCache<List<String>>()
        val payload = listOf("com.a", "com.b")
        cache.store(payload, 1_000L, cache.generation())
        assertSame(payload, cache.freshPayload(2_000L, 15_000L))
    }

    @Test
    fun clearBumpsGeneration() {
        val cache = RunningAppsUsageCache<String>()
        val before = cache.generation()
        cache.clear()
        assertTrue(cache.generation() > before)
    }

    @Test
    fun storeAfterClearWithStaleGenerationIsIgnored() {
        val cache = RunningAppsUsageCache<String>()
        val scanGeneration = cache.generation()
        cache.store("old", 1_000L, scanGeneration)
        cache.clear()
        // The scan finished after the clear: its pre-change result must not resurrect.
        cache.store("stale", 2_000L, scanGeneration)
        assertNull(cache.freshPayload(2_000L, 15_000L))
    }

    @Test
    fun storeWithCurrentGenerationAfterClearWorks() {
        val cache = RunningAppsUsageCache<String>()
        cache.store("old", 1_000L, cache.generation())
        cache.clear()
        cache.store("new", 2_000L, cache.generation())
        assertEquals("new", cache.freshPayload(2_000L, 15_000L))
    }
}