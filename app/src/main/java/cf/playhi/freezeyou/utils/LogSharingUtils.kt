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
 * Both forms are offered at once: the text, which a messenger or a notes application takes, and a
 * file, which a file manager or a mail client takes. Nothing has to be chosen in advance, and the
 * receiving application picks what it understands.
 */
object LogSharingUtils {

    private const val FILE_PROVIDER_AUTHORITY = "cf.playhi.freezeyou.fileprovider"

    /**
     * Past this size the text is left out and only the file is attached. An intent extra crosses a
     * binder transaction with about a megabyte to spare for everything in it, and a long log would
     * take the whole share down with it rather than arrive truncated.
     */
    private const val MAX_TEXT_EXTRA_CHARS = 200_000

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
        if (text.length <= MAX_TEXT_EXTRA_CHARS) {
            intent.putExtra(Intent.EXTRA_TEXT, text)
        }

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
                // Sharing the text alone still works, so this is not worth reporting.
                e.printStackTrace()
            }
        }

        if (!intent.hasExtra(Intent.EXTRA_TEXT) && !intent.hasExtra(Intent.EXTRA_STREAM)) {
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
