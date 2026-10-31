package cf.playhi.freezeyou.utils

import android.app.Application
import android.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class GZipUtilsTest {

    @Test
    fun backupSurvivesCompressAndDecompress() {
        val json = """{"generalSettings_boolean":[{"noCaution":true}],"userTimeScheduledTasks":[]}"""
        assertEquals(json, GZipUtils.gzipDecompress(GZipUtils.gzipCompress(json)))
    }

    @Test
    fun backupsLargerThanThePreviousIntentLimitSurviveRoundTrip() {
        // Restores are handed to the chooser through a cache file now; before that, anything past
        // 256K chars was rejected even though the app itself can export backups that large.
        val json = """{"oneKeyList":[{"okff":"${"x".repeat(300 * 1024)}"}]}"""
        assertEquals(json, GZipUtils.gzipDecompress(GZipUtils.gzipCompress(json)))
    }

    @Test
    fun outputPastTheDecompressionLimitIsRejected() {
        // A tiny gzip of zeros inflates past any sane backup size; without the bound this is the
        // classic OutOfMemoryError from a wrong or crafted file.
        val compressed = Base64.encodeToString(
            gzipOfZeros(MAX_DECOMPRESSED_BYTES + 1),
            Base64.DEFAULT
        )
        assertEquals("", GZipUtils.gzipDecompress(compressed))
    }

    @Test
    fun tinyOutputIsStillDecompressed() {
        val compressed = Base64.encodeToString(gzipOfZeros(1024), Base64.DEFAULT)
        val decompressed = GZipUtils.gzipDecompress(compressed)
        assertEquals(1024, decompressed.length)
    }

    @Test
    fun malformedInputReturnsEmptyInsteadOfThrowing() {
        assertEquals("", GZipUtils.gzipDecompress(""))
        assertEquals("", GZipUtils.gzipDecompress("not base64 at all"))
        assertEquals(
            "",
            GZipUtils.gzipDecompress(
                Base64.encodeToString("not a gzip stream".toByteArray(), Base64.DEFAULT)
            )
        )
    }

    private fun gzipOfZeros(totalBytes: Int): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { gzip ->
            val chunk = ByteArray(64 * 1024)
            var remaining = totalBytes
            while (remaining > 0) {
                val length = minOf(remaining, chunk.size)
                gzip.write(chunk, 0, length)
                remaining -= length
            }
        }
        return out.toByteArray()
    }

    private companion object {
        const val MAX_DECOMPRESSED_BYTES = 16 * 1024 * 1024
    }
}
