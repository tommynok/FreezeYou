package cf.playhi.freezeyou.utils

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.appcompat.app.AlertDialog
import cf.playhi.freezeyou.app.FreezeYouAlertDialogBuilder

object AlertDialogUtils {
    @JvmStatic
    fun buildAlertDialog(
        context: Context,
        icon: Int,
        message: Int,
        title: Int
    ): AlertDialog.Builder {
        val builder = FreezeYouAlertDialogBuilder(context)
        builder.setIcon(icon)
        builder.setTitle(title)
        builder.setMessage(message)
        return builder
    }

    @JvmStatic
    fun buildAlertDialog(
        context: Context,
        icon: Drawable?,
        message: CharSequence?,
        title: CharSequence?
    ): AlertDialog.Builder {
        val builder = FreezeYouAlertDialogBuilder(context)
        builder.setIcon(icon)
        builder.setTitle(title)
        builder.setMessage(message)
        return builder
    }
}
