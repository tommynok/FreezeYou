package cf.playhi.freezeyou.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RunningAppsStatsTest {
    private val packages = setOf("com.example.app", "com.example.other")

    @Test
    fun combinesSubprocessesByPackageAndUsesOnlySecondCpuSample() {
        val usage = parse(
            """
                Total PSS by process:
                  1,024K: com.example.app (pid 101 / activities)
                  512K: com.example.app:worker (pid 102)
                  256K: com.example.other (pid 103)
                __FREEZEYOU_CPU__
                  PID %CPU ARGS
                  101 99.0 com.example.app
                  102 98.0 com.example.app:worker
                  103 40.0 com.example.other
                  PID %CPU ARGS
                  101 12.5 com.example.app
                  102 3.5 com.example.app:worker
                  103 0.0 com.example.other
            """
        )
        assertEquals(RunningAppsUtils.ResourceUsage(16.0, 1536L * 1024), usage["com.example.app"])
        assertEquals(RunningAppsUtils.ResourceUsage(0.0, 256L * 1024), usage["com.example.other"])
    }

    @Test
    fun recognizesToyboxSortBracketsInBatchHeaders() {
        val usage = parse(
            """
                  512K: com.example.app (pid 101)
                __FREEZEYOU_CPU__
                  PID %CPU[ARGS]
                  101 90.0 com.example.app
                  PID %CPU[ARGS]
                  101 12.0 com.example.app
            """
        )
        assertEquals(RunningAppsUtils.ResourceUsage(12.0, 512L * 1024), usage["com.example.app"])
        assertEquals(
            usage,
            parse("""
                  512K: com.example.app (pid 101)
                __FREEZEYOU_CPU__
                  PID[%CPU] ARGS
                  PID[%CPU] ARGS
                  101 12.0 com.example.app
            """)
        )
    }

    @Test
    fun aSingleCpuSampleIsUnavailableRatherThanAnIntervalMeasurement() {
        val usage = parse(
            """
                  512K: com.example.app (pid 101)
                __FREEZEYOU_CPU__
                  PID %CPU ARGS
                  101 90.0 com.example.app
            """
        )
        assertEquals(RunningAppsUtils.ResourceUsage(null, 512L * 1024), usage["com.example.app"])
    }

    @Test
    fun vanishedProcessDoesNotKeepItsFirstSampleCpu() {
        val usage = parse(
            """
                  512K: com.example.app (pid 101)
                __FREEZEYOU_CPU__
                  PID %CPU ARGS
                  101 90.0 com.example.app
                  PID %CPU ARGS
                  103 4.0 com.example.other
            """
        )
        assertNull(usage["com.example.app"]!!.cpuPercent)
        assertEquals(RunningAppsUtils.ResourceUsage(4.0, null), usage["com.example.other"])
    }

    @Test
    fun partialProcessCoverageIsNotReportedAsACompletePackageTotal() {
        val usage = parse(
            """
                  512K: com.example.app (pid 101)
                  128K: com.example.app:worker (pid 102)
                __FREEZEYOU_CPU__
                  PID %CPU ARGS
                  PID %CPU ARGS
                  101 12.0 com.example.app
            """
        )
        assertEquals(RunningAppsUtils.ResourceUsage(null, 640L * 1024), usage["com.example.app"])
    }

    @Test
    fun duplicateNamesWithDifferentPidsAreSummedOnceEach() {
        val usage = parse(
            """
                  512K: com.example.app (pid 101)
                  128K: com.example.app (pid 102)
                  512K: com.example.app (pid 101)
                __FREEZEYOU_CPU__
                  PID %CPU ARGS
                  PID %CPU ARGS
                  101 150.0 com.example.app
                  102 25.0 com.example.app
            """
        )
        assertEquals(RunningAppsUtils.ResourceUsage(175.0, 640L * 1024), usage["com.example.app"])
    }

    @Test
    fun pidReuseDoesNotJoinDifferentProcesses() {
        val usage = parse(
            """
                  512K: com.example.app (pid 101)
                __FREEZEYOU_CPU__
                  PID %CPU ARGS
                  PID %CPU ARGS
                  101 12.0 com.example.other
            """
        )
        assertTrue(usage.isEmpty())
    }

    @Test
    fun ignoresNativeProcessesAndUnrelatedPackagePrefixes() {
        val usage = parse(
            """
                  512K: surfaceflinger (pid 101)
                  128K: com.example.app.unrelated (pid 102)
                  64K: :worker (pid 103)
                __FREEZEYOU_CPU__
                  PID %CPU ARGS
                  PID %CPU ARGS
                  101 10.0 surfaceflinger
                  102 12.0 com.example.app.unrelated
                  103 1.0 :worker
            """
        )
        assertTrue(usage.isEmpty())
    }

    @Test
    fun missingCpuOutputStillAllowsMemory() {
        assertEquals(
            RunningAppsUtils.ResourceUsage(null, 512L * 1024),
            parse("512K: com.example.app (pid 101)")["com.example.app"]
        )
    }

    @Test
    fun malformedNumbersAndDeniedCommandsDoNotBecomeZeroUsage() {
        assertTrue(parse("Permission denied\n__FREEZEYOU_CPU__\ntop: bad -o").isEmpty())
        assertTrue(parse("").isEmpty())
        val usage = parse(
            """
                  999999999999999999999999K: com.example.app (pid 101)
                  9223372036854775807K: com.example.app (pid 102)
                  512K: com.example.other (pid 9999999999999999999)
                __FREEZEYOU_CPU__
                  PID %CPU ARGS
                  PID %CPU ARGS
                  101 NaN com.example.app
                  102 1.2.3 com.example.app
                  9999999999999999999 1.0 com.example.other
            """
        )
        assertTrue(usage.isEmpty())
    }

    private fun parse(output: String) = RunningAppsUtils.parseResourceUsage(output.trimIndent(), packages)
}
