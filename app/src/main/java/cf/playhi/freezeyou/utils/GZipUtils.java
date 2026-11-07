package cf.playhi.freezeyou.utils;

import android.text.TextUtils;
import android.util.Base64;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public final class GZipUtils {
    // Reference: http://www.cnblogs.com/whoislcj/p/5473806.html

    // A backup is a small JSON document; anything past this is a crafted or corrupt file whose
    // only other outcome is an OutOfMemoryError.
    private static final int MAX_DECOMPRESSED_BYTES = 16 * 1024 * 1024;

    /**
     * @param unGzipStr String to compress
     * @return Compressed string, or "" on failure
     */
    public static String gzipCompress(String unGzipStr) {

        if (TextUtils.isEmpty(unGzipStr)) {
            return "";
        }
        try {
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            GZIPOutputStream gzip = new GZIPOutputStream(byteArrayOutputStream);
            gzip.write(unGzipStr.getBytes());
            gzip.close();
            byte[] b = byteArrayOutputStream.toByteArray();
            byteArrayOutputStream.flush();
            byteArrayOutputStream.close();
            return Base64.encodeToString(b, Base64.DEFAULT);
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return "";
    }

    /**
     * @param gzipStr Compressed string
     * @return Decompressed string, or "" when the input is invalid, oversized or cannot be inflated
     */
    public static String gzipDecompress(String gzipStr) {
        if (TextUtils.isEmpty(gzipStr)) {
            return "";
        }
        try (ByteArrayInputStream in =
                     new ByteArrayInputStream(Base64.decode(gzipStr, Base64.DEFAULT));
             GZIPInputStream gzip = new GZIPInputStream(in);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[64 * 1024];
            int total = 0;
            int n;
            while ((n = gzip.read(buffer, 0, buffer.length)) > 0) {
                total += n;
                if (total > MAX_DECOMPRESSED_BYTES) {
                    return "";
                }
                out.write(buffer, 0, n);
            }
            return out.toString();
        } catch (IOException | IllegalArgumentException e) {
            e.printStackTrace();
        } catch (OutOfMemoryError e) {
            e.printStackTrace();
        }
        return "";
    }
}
