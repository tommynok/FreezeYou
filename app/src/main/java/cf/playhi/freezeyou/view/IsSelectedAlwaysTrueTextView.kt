package cf.playhi.freezeyou.view

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView

class IsSelectedAlwaysTrueTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatTextView(context, attrs, defStyleAttr) {

    override fun isSelected(): Boolean {
        return true
    }
}
