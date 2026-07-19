package cf.playhi.freezeyou.utils

import android.content.Context
import android.widget.Toast

object ToastUtils {
    @JvmStatic
    fun showToast(context: Context, id: Int) {
        showLongToast(context, id)
    }

    @JvmStatic
    fun showToast(context: Context, string: String?) {
        if (string != null) showLongToast(context, string)
    }

    @JvmStatic
    fun showLongToast(context: Context, id: Int) {
        Toast.makeText(context, id, Toast.LENGTH_LONG).show()
    }

    @JvmStatic
    fun showLongToast(context: Context, string: String?) {
        if (string != null) Toast.makeText(context, string, Toast.LENGTH_LONG).show()
    }

    @JvmStatic
    fun showShortToast(context: Context, id: Int) {
        Toast.makeText(context, id, Toast.LENGTH_SHORT).show()
    }

    @JvmStatic
    fun showShortToast(context: Context, string: String?) {
        if (string != null) Toast.makeText(context, string, Toast.LENGTH_SHORT).show()
    }
}
