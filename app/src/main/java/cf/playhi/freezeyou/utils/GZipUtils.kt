package cf.playhi.freezeyou.utils

import android.text.TextUtils
import android.util.Base64
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.UnsupportedEncodingException
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

object GZipUtils {
    //参考 http://www.cnblogs.com/whoislcj/p/5473806.html
    /**
     * @param unGzipStr 被压缩字符串
     * @return 压缩后字符串，失败返回 String s=""
     */
    @JvmStatic
    fun gzipCompress(unGzipStr: String?): String {
        if (TextUtils.isEmpty(unGzipStr)) {
            return ""
        }
        try {
            val byteArrayOutputStream = ByteArrayOutputStream()
            val gzip = GZIPOutputStream(byteArrayOutputStream)
            gzip.write(unGzipStr!!.toByteArray())
            gzip.close()
            val b = byteArrayOutputStream.toByteArray()
            byteArrayOutputStream.flush()
            byteArrayOutputStream.close()
            return Base64.encodeToString(b, Base64.DEFAULT)
        } catch (e: UnsupportedEncodingException) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return ""
    }

    /**
     * @param gzipStr 已压缩过的 String
     * @return 解压缩后的 String
     */
    @JvmStatic
    fun gzipDecompress(gzipStr: String?): String {
        if (TextUtils.isEmpty(gzipStr)) {
            return ""
        }
        try {
            val t = Base64.decode(gzipStr, Base64.DEFAULT)
            val out = ByteArrayOutputStream()
            val `in` = ByteArrayInputStream(t)
            val gzip = GZIPInputStream(`in`)
            val buffer = ByteArray(1024 * 1024)
            var n: Int
            while (gzip.read(buffer, 0, buffer.size).also { n = it } > 0) {
                out.write(buffer, 0, n)
            }
            gzip.close()
            `in`.close()
            out.close()
            return out.toString()
        } catch (e: IOException) {
            e.printStackTrace()
        } catch (e: IllegalArgumentException) {
            e.printStackTrace()
        }
        return ""
    }
}
