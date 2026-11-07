package cf.playhi.freezeyou.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class RunningAppsUsageCacheTest {
    @Test
    fun freshPayloadReturnedWithinWindow() {
        val cache = RunningAppsUsageCache<String>()
        cache.store("scan", 1_000L)
        assertEquals("scan", cache.freshPayload(1_000L, 15_000L))
        assertEquals("scan", cache.freshPayload(10_000L, 15_000L))
    }

    @Test
    fun ageBoundaryIsInclusive() {
        val cache = RunningAppsUsageCache<String>()
        cache.store("scan", 1_000L)
        assertEquals("scan", cache.freshPayload(16_000L, 15_000L))
        assertNull(cache.freshPayload(16_001L, 15_000L))
    }

    @Test
    fun expiredPayloadIsNotReturned() {
        val cache = RunningAppsUsageCache<String>()
        cache.store("scan", 1_000L)
        assertNull(cache.freshPayload(60_000L, 15_000L))
    }

    @Test
    fun clockRollbackCountsAsStale() {
        val cache = RunningAppsUsageCache<String>()
        cache.store("scan", 10_000L)
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
        cache.store("scan", 1_000L)
        cache.clear()
        assertNull(cache.freshPayload(1_000L, 15_000L))
    }

    @Test
    fun storeNullIsIgnored() {
        val cache = RunningAppsUsageCache<String>()
        cache.store("scan", 1_000L)
        cache.store(null, 2_000L)
        assertEquals("scan", cache.freshPayload(2_000L, 15_000L))
    }

    @Test
    fun storeOverwritesPreviousSnapshotAndTimestamp() {
        val cache = RunningAppsUsageCache<String>()
        cache.store("old", 1_000L)
        cache.store("new", 20_000L)
        assertEquals("new", cache.freshPayload(20_000L, 15_000L))
        assertNull(cache.freshPayload(40_000L, 15_000L))
    }

    @Test
    fun freshPayloadReturnsStoredInstance() {
        val cache = RunningAppsUsageCache<List<String>>()
        val payload = listOf("com.a", "com.b")
        cache.store(payload, 1_000L)
        assertSame(payload, cache.freshPayload(2_000L, 15_000L))
    }
}