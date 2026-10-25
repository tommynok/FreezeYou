package cf.playhi.freezeyou.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The running-process scan of the "Running" filter, on the root path.
 *
 * That filter needs elevated access, so the app streams a /proc scan plus two `ps` fallbacks into
 * `su` and reads the package names back off its stdout. Two things about that exchange used to be
 * unbounded. Nothing put a deadline on the read, so a shell that never answered — an approval
 * prompt nobody tapped, a `su` that stopped reading — held the thread that generates the main list
 * for as long as the process lived, and the list stayed wedged with it. And nothing emptied
 * stderr, so once the OS pipe buffer behind it filled, the shell blocked on its own next warning
 * and produced no stdout either: a deadlock that looks exactly like the first one from this side.
 *
 * Both are covered here without a device and without a shell. The parser is fed the bytes the
 * command list is documented to produce; the wedged shell is a stream that stays open and produces
 * nothing, which is the whole of what a hung `su` amounts to for the reader. The parser tests
 * matter as much as the deadline ones — the fix moved the read onto a worker thread, and the set
 * it hands back has to stay the set it always handed back.
 */
class ProcessUtilsTest {

    @Test
    fun allThreeSourcesMergeIntoOneSetOfPackageNames() {
        val packages = parse(
            "com.example.one\n" +
                "com.example.two:push\n" +
                "  com.example.three  \n" +
                "$primaryMarker\n" +
                "com.example.one\n" +
                "$fallbackMarker\n" +
                "USER           PID  PPID     VSZ    RSS WCHAN            ADDR S NAME\n" +
                "u0_a99        4321   567 2445000 187000 0                   0 S com.example.four\n"
        )
        assertEquals(
            setOf(
                "com.example.one",
                // A ":push" process belongs to the package in front of it, and the set is a set of
                // packages, so the suffix goes.
                "com.example.two",
                "com.example.three",
                "com.example.four"
            ),
            packages
        )
    }

    @Test
    fun thePlainPsSectionLosesItsHeaderAndKeepsItsLastColumn() {
        val packages = parse(
            "$fallbackMarker\n" +
                "USER           PID  PPID     VSZ    RSS WCHAN            ADDR S NAME\n" +
                "root             1     0   25792   8548 0                   0 S init\n"
        )
        assertEquals(setOf("init"), packages)
    }

    @Test
    fun aNameStraightAfterThePrimaryMarkerIsDataAndNotAHeader() {
        // "ps -A -o NAME=" prints no header line, unlike the plain "ps -A" of the fallback
        // section, so the skip must not reach this far or the first running process is lost.
        assertEquals(setOf("u0_a123"), parse("$primaryMarker\nu0_a123\n"))
    }

    @Test
    fun aScanWithNothingInItIsNoPackagesAndNotAFailure() {
        assertEquals(emptySet<String>(), parse(""))
        assertEquals(emptySet<String>(), parse("\n   \n\t\n"))
        assertEquals(emptySet<String>(), parse("$primaryMarker\n$fallbackMarker\n"))
    }

    @Test
    fun aColonAtTheStartOfANameIsPartOfIt() {
        // The separator is only a separator between a package and its process, so there has to be
        // a package in front of it.
        assertEquals(setOf(":strange"), parse(":strange\n"))
    }

    @Test
    fun aShellThatStopsAnsweringIsGivenUpOnInsteadOfWaitedFor() {
        val silent = object : InputStream() {
            override fun read(): Int {
                Thread.sleep(60_000)
                return -1
            }
        }
        val startedAt = System.nanoTime()
        assertThrows(TimeoutException::class.java) {
            ProcessUtils.parseRunningPackages(silent, 200L)
        }
        val waitedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)
        assertTrue("waited ${waitedMs}ms on a stream that never produced a byte", waitedMs < 5_000)
    }

    @Test
    fun aReadThatIgnoresTheInterruptIsReleasedByTheStreamBeingClosed() {
        val closed = AtomicBoolean(false)
        val stillReading = AtomicBoolean(true)
        val deaf = object : InputStream() {
            override fun read(): Int {
                // Stands in for a read wedged below the JVM, where an interrupt has nothing to
                // land on. All it notices is that the stream was closed, which is the release the
                // bounded overload adds: without it the worker outlives the scan it was started
                // for, and every refresh of the filter leaves another one behind.
                while (!closed.get()) {
                    try {
                        Thread.sleep(20)
                    } catch (ignored: InterruptedException) {
                    }
                }
                stillReading.set(false)
                return -1
            }

            override fun close() {
                closed.set(true)
            }
        }

        assertThrows(TimeoutException::class.java) {
            ProcessUtils.parseRunningPackages(deaf, 200L)
        }

        val deadline = System.currentTimeMillis() + 3_000
        while (stillReading.get() && System.currentTimeMillis() < deadline) {
            Thread.sleep(20)
        }
        assertFalse(
            "the worker was still reading a stream that had been closed under it",
            stillReading.get()
        )
    }

    @Test
    fun cleaningUpAfterAShellDoesNotThrowWhateverStateItIsIn() {
        val process = RecordingProcess()
        val writer = DataOutputStream(ByteArrayOutputStream())
        writer.close()

        ProcessUtils.destroyProcess(writer, process)
        ProcessUtils.destroyProcess(null, null)
        ProcessUtils.destroyProcess(null, process)

        assertEquals(2, process.destroyCalls)
    }

    private fun parse(output: String): Set<String> =
        ProcessUtils.parseRunningPackages(ByteArrayInputStream(output.toByteArray()))

    private class RecordingProcess : Process() {
        var destroyCalls = 0
        override fun destroy() {
            destroyCalls++
        }

        override fun exitValue() = 0
        override fun getInputStream(): InputStream = ByteArrayInputStream(ByteArray(0))
        override fun getErrorStream(): InputStream = ByteArrayInputStream(ByteArray(0))
        override fun getOutputStream() = ByteArrayOutputStream()
    }

    private companion object {
        /**
         * Read out of the command list rather than written down again, so that the test cannot
         * drift away from the markers the shell is actually asked to print.
         */
        val primaryMarker = markerInCommands(1)
        val fallbackMarker = markerInCommands(3)

        fun markerInCommands(index: Int) =
            ProcessUtils.RUNNING_PACKAGES_COMMANDS[index].substringAfter("echo ")
    }
}
