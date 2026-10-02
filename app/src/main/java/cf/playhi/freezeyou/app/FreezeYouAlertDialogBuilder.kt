package cf.playhi.freezeyou.app

import android.content.Context
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * The one place where dialogs are built.
 *
 * It always returns a Material dialog: every AppTheme.* in styles.xml is a Material3 theme on
 * every API level, and Material3 is what draws the rounded, surface-coloured dialog. It used to
 * fall back to androidx AlertDialog.Builder for the dark and black palettes, which is exactly
 * where the dialogs came out as flat AppCompat rectangles.
 */
@JvmOverloads
fun FreezeYouAlertDialogBuilder(
    context: Context,
    overrideThemeResId: Int = 0
): AlertDialog.Builder {
    return MaterialAlertDialogBuilder(context, overrideThemeResId)
}
