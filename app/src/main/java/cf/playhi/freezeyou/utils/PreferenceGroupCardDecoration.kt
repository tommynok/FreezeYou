package cf.playhi.freezeyou.utils

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.core.util.Pair
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceGroupAdapter
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.min

/**
 * Draws every [PreferenceCategory] group of a preference list as one rounded card behind
 * its rows (Material 3 expressive look). Children of a category share a single card: the
 * first row gets the top corners, the last one the bottom corners, rows in between sit on
 * straight edges. Rows above the first category form a group of their own, so screens
 * without any categories render as a single card.
 */
class PreferenceGroupCardDecoration(private val cardColor: Int) : RecyclerView.ItemDecoration() {

    override fun onDraw(canvas: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        val adapter = parent.adapter as? PreferenceGroupAdapter ?: return
        val count = adapter.itemCount
        if (count == 0) return

        // Map every adapter position to the inclusive [first, last] span of its card.
        val spans = HashMap<Int, Pair<Int, Int>>(count)
        var spanStart = -1
        for (pos in 0 until count) {
            val item = adapter.getItem(pos)
            val isHeader = item is PreferenceCategory
            if (isHeader) {
                if (spanStart != -1) {
                    // close the previous span: it ends before this header
                    for (p in spanStart until pos) spans[p] = Pair(spanStart, pos - 1)
                    spanStart = -1
                }
                continue
            }
            if (spanStart == -1) spanStart = pos
        }
        if (spanStart != -1) {
            for (p in spanStart until count) spans[p] = Pair(spanStart, count - 1)
        }
        if (spans.isEmpty()) return

        val density = parent.resources.displayMetrics.density
        val radius = 20f * density
        val inset = 4f * density
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = cardColor }

        for (childIndex in 0 until parent.childCount) {
            val child = parent.getChildAt(childIndex)
            val pos = parent.getChildAdapterPosition(child)
            val span = spans[pos] ?: continue
            val isFirst = pos == span.first
            val isLast = pos == span.second

            val left = inset
            val right = parent.width - inset
            // Rows of one card overlap by a pixel so anti-aliased edges leave no seams.
            val top = child.top.toFloat() - if (isFirst) 0f else 1f
            val bottom = child.bottom.toFloat() + if (isLast) 0f else 1f

            val topLeft = if (isFirst) radius else 0f
            val topRight = if (isFirst) radius else 0f
            val bottomLeft = if (isLast) radius else 0f
            val bottomRight = if (isLast) radius else 0f

            val path = Path()
            path.addRoundRect(
                left, top, right, bottom,
                floatArrayOf(topLeft, topLeft, topRight, topRight, bottomRight, bottomRight, bottomLeft, bottomLeft),
                Path.Direction.CW
            )
            canvas.drawPath(path, paint)
        }
    }
}
