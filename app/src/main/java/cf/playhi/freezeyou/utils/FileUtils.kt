package cf.playhi.freezeyou.utils

import android.content.Context
import android.os.Build
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

object FileUtils {
    @JvmStatic
    @Throws(IOException::class)
    fun deleteAllFiles(file: File?, deleteSelfFolder: Boolean) {
        if (file == null) {
            return
        }
        if (file.exists()) {
            if (file.isFile) {
                if (!file.delete()) throw IOException(file.absolutePath + " delete failed")
            } else if (file.isDirectory) {
                val files = file.listFiles()
                if (files != null) {
                    for (f in files) {
                        deleteAllFiles(f, true)
                    }
                }
                if (deleteSelfFolder) {
                    if (!file.delete()) {
                        throw IOException(file.absolutePath + " delete failed")
                    }
                }
            }
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun copyFile(inputStream: InputStream?, apkFilePath: String) {
        if (inputStream == null) {
            throw IOException("InputStream is null")
        }
        if (Build.VERSION.SDK_INT < 26) {
            val out = FileOutputStream(apkFilePath)
            val buffer = ByteArray(1024 * 1024)
            var bytesRead: Int
            while (inputStream.read(buffer).also { bytesRead = it } >= 0) {
                out.write(buffer, 0, bytesRead)
            }
            out.close()
            inputStream.close()
        } else {
            Files.copy(inputStream, File(apkFilePath).toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    @JvmStatic
    fun clearIconCache(context: Context): Boolean {
        return try {
            deleteAllFiles(File(context.filesDir.toString() + "/icon"), false)
            deleteAllFiles(File(context.cacheDir.toString() + "/icon"), false)
            true
        } catch (e: IOException) {
            e.printStackTrace()
            false
        }
    }
}
