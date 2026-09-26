package cf.playhi.freezeyou.utils

import android.app.Activity
import android.content.Intent
import androidx.core.content.FileProvider
import cf.playhi.freezeyou.R
import cf.playhi.freezeyou.utils.ToastUtils.showToast
import java.io.File

/**
 * Handing a log to the system share sheet, from the crash dialog and from the log viewer alike.
 *
 * The log travels as a file attachment only. It used to go as text alongside the file, but
 * messengers such as Telegram then paste the whole listing into chat, where the message length
 * limit chops it into a dozen forwarded bubbles on top of the file — noise nobody asked for. A
 * bare EXTRA_STREAM shares the file alone; applications that cannot take files simply do not
 * appear in the chooser.
 */
object LogSharingUtils {

    private const val FILE_PROVIDER_AUTHORITY = "cf.playhi.freezeyou.fileprovider"

    /**
     * @param existingFile a file that already holds this log — the crash log has one. When null, a
     *                     copy is written into the cache directory, which the file provider serves
     *                     and the system reclaims on its own.
     */
    @JvmStatic
    @JvmOverloads
    fun shareLog(
        activity: Activity,
        subject: String,
        text: String,
        existingFile: File? = null
    ) {
        val intent = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, subject)

        val file = existingFile ?: writeToCache(activity, text)
        if (file != null) {
            try {
                intent
                    .putExtra(
                        Intent.EXTRA_STREAM,
                        FileProvider.getUriForFile(activity, FILE_PROVIDER_AUTHORITY, file)
                    )
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (!intent.hasExtra(Intent.EXTRA_STREAM)) {
            showToast(activity, R.string.failed)
            return
        }

        try {
            activity.startActivity(
                Intent.createChooser(intent, activity.getString(R.string.share))
            )
        } catch (e: Exception) {
            e.printStackTrace()
            showToast(activity, R.string.failed)
        }
    }

    private fun writeToCache(activity: Activity, text: String): File? {
        return try {
            val dir = File(activity.cacheDir, "log")
            if (!dir.isDirectory && !dir.mkdirs()) return null
            val file = File(dir, "freezeyou-log-${System.currentTimeMillis()}.log")
            file.writeText(text)
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
