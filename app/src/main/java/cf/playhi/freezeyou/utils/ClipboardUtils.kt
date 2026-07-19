package cf.playhi.freezeyou.utils

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

object ClipboardUtils {
    @JvmStatic
    fun copyToClipboard(context: Context, data: String?): Boolean {
        var success = false
        val copy = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager?
        val clip = ClipData.newPlainText(data, data)
        if (copy != null) {
            copy.setPrimaryClip(clip)
            success = true
        }
        return success
    }

    @JvmStatic
    fun getClipboardItemText(context: Context): CharSequence {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager?
        if (cm == null || !cm.hasPrimaryClip()) {
            return ""
        }
        val clip = cm.primaryClip
        return if (clip == null || clip.itemCount <= 0) {
            ""
        } else clip.getItemAt(0).text
    }
}
