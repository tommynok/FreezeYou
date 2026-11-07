package cf.playhi.freezeyou.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Two bounds that used to cost a device seconds of silence.
 *
 * On a device without root, or with the request denied, nothing remembered that the probe had
 * failed: every elevated call — an elevated launch, a force stop, the diagnosis screen — walked
 * all seven candidates at five seconds each and could raise the manager prompt again. The failure
 * is now remembered for a TTL, and the rule that turns a timestamp into "still cached" is kept
 * pure so it can be checked without a root shell. The second bound is the batch deadline: a flat
 * 60 s could not cover a 40-command chunk on a slow device, where a cold `app_process` or `pm`
 * start costs a second or two per command, so a session that was still working got killed and the
 * commands already run were reported as failed. The deadline now grows with the chunk.
 */
class RootDiscoveryTest {

    @Test
    fun `a failure still inside the ttl blocks the next probe`() {
        assertTrue(RootUtils.isNegativeCacheActive(1_000L, 1_000L, 30_000L))
        assertTrue(RootUtils.isNegativeCacheActive(30_999L, 1_000L, 30_000L))
    }

    @Test
    fun `a failure at or past the ttl allows a probe again`() {
        assertFalse(RootUtils.isNegativeCacheActive(31_000L, 1_000L, 30_000L))
        assertFalse(RootUtils.isNegativeCacheActive(100_000L, 1_000L, 30_000L))
    }

    @Test
    fun `no recorded failure is no cache`() {
        assertFalse(RootUtils.isNegativeCacheActive(1_000L, 0L, 30_000L))
    }

    @Test
    fun `a clock that moved backwards does not stretch the cache`() {
        // Otherwise the elapsed time is negative, which reads as "inside every TTL" and would
        // keep root refused until the clock caught up.
        assertFalse(RootUtils.isNegativeCacheActive(500L, 1_000L, 30_000L))
    }

    @Test
    fun `resetCache forgets both the working binary and the failure`() {
        val binary = RootUtils::class.java.getDeclaredField("sCachedSuBinary")
        val failure = RootUtils::class.java.getDeclaredField("sLastFailedDiscoveryTimeMs")
        binary.isAccessible = true
        failure.isAccessible = true
        binary.set(null, "/system/xbin/su")
        failure.setLong(null, 1_234L)

        RootUtils.resetCache()

        assertNull(binary.get(null))
        assertEquals(0L, failure.getLong(null))
    }

    @Test
    fun `a full chunk gets the base allowance plus two seconds per command`() {
        assertEquals(110_000L, PrivilegedShellUtils.computeSessionTimeoutMs(40))
    }

    @Test
    fun `the deadline grows by one command allowance at a time`() {
        assertEquals(30_000L, PrivilegedShellUtils.computeSessionTimeoutMs(0))
        assertEquals(32_000L, PrivilegedShellUtils.computeSessionTimeoutMs(1))
        assertEquals(34_000L, PrivilegedShellUtils.computeSessionTimeoutMs(2))
        assertEquals(
            2_000L,
            PrivilegedShellUtils.computeSessionTimeoutMs(2) -
                PrivilegedShellUtils.computeSessionTimeoutMs(1)
        )
    }
}
