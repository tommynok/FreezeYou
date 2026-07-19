package cf.playhi.freezeyou.app

import android.content.Context
import android.view.MotionEvent
import androidx.appcompat.app.AlertDialog

open class ObsdAlertDialog(context: Context) : AlertDialog(context) {
    private var isObsdValue = false
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        isObsdValue = ev.flags and MotionEvent.FLAG_WINDOW_IS_OBSCURED != 0
        return super.dispatchTouchEvent(ev)
    }

    fun isObsd(): Boolean {
        return isObsdValue
    }
}
