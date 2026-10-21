package cf.playhi.freezeyou.view

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.annotation.NonNull
import androidx.annotation.Nullable
import androidx.recyclerview.widget.RecyclerView
import cf.playhi.freezeyou.utils.ThemeUtils
import kotlin.math.roundToInt

/**
 * Ergonomic, unified fast scroller for FreezeYou application lists and grids.
 * Guarantees a minimum thumb height in both list and grid presentations, provides an
 * expanded touch hit area, and accents the thumb whenever the user touches or scrolls the screen.
 */
class AppListFastScroller(@NonNull recyclerView: RecyclerView) {

    private var recyclerView: RecyclerView? = null

    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val thumbRect = RectF()

    private var thumbTop: Int = 0
    private var thumbHeight: Int = 0

    private var isDragging: Boolean = false
    private var activePointerId: Int = MotionEvent.INVALID_POINTER_ID
    private var dragStartY: Float = 0f
    private var dragStartThumbTop: Int = 0

    private var animAlpha: Float = 0f
    private var fadeAnimator: ValueAnimator? = null

    private val hideRunnable = Runnable {
        fadeOut()
    }

    private val density: Float = recyclerView.context.resources.displayMetrics.density

    // Metrics
    private val pillWidth: Float = 8.5f * density
    private val pillCornerRadius: Float = pillWidth / 2f
    private val pillRightMargin: Float = 2.0f * density
    private val minThumbHeight: Int = (54f * density).roundToInt()
    private val topMargin: Int = (4f * density).roundToInt()
    private val bottomMargin: Int = (4f * density).roundToInt()

    // Expanded touch target for reliable thumb grabbing
    private val touchWidth: Float = 36f * density
    private val touchVerticalPadding: Float = 12f * density

    private val itemDecoration = object : RecyclerView.ItemDecoration() {
        override fun onDrawOver(
            @NonNull c: Canvas,
            @NonNull parent: RecyclerView,
            @NonNull state: RecyclerView.State
        ) {
            drawScroller(c, parent)
        }
    }

    private val touchListener = object : RecyclerView.OnItemTouchListener {
        override fun onInterceptTouchEvent(@NonNull rv: RecyclerView, @NonNull e: MotionEvent): Boolean {
            return handleInterceptTouchEvent(rv, e)
        }

        override fun onTouchEvent(@NonNull rv: RecyclerView, @NonNull e: MotionEvent) {
            handleTouchEvent(rv, e)
        }

        override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
            // No-op
        }
    }

    private val scrollListener = object : RecyclerView.OnScrollListener() {
        override fun onScrolled(@NonNull rv: RecyclerView, dx: Int, dy: Int) {
            if (!isDragging) {
                updatePositionFromScroll(rv)
                showThumb()
                scheduleHide(HIDE_DELAY_MS)
            }
        }
    }

    private val layoutChangeListener = View.OnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
        val rv = recyclerView ?: return@OnLayoutChangeListener
        if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) {
            updatePositionFromScroll(rv)
        }
    }

    init {
        attachToRecyclerView(recyclerView)
    }

    fun attachToRecyclerView(@Nullable target: RecyclerView?) {
        if (recyclerView === target) return
        detach()
        if (target == null) return
        recyclerView = target
        target.addItemDecoration(itemDecoration)
        target.addOnItemTouchListener(touchListener)
        target.addOnScrollListener(scrollListener)
        target.addOnLayoutChangeListener(layoutChangeListener)
        updatePositionFromScroll(target)
    }

    fun detach() {
        val currentRv = recyclerView ?: return
        cancelHide()
        fadeAnimator?.cancel()
        currentRv.removeItemDecoration(itemDecoration)
        currentRv.removeOnItemTouchListener(touchListener)
        currentRv.removeOnScrollListener(scrollListener)
        currentRv.removeOnLayoutChangeListener(layoutChangeListener)
        recyclerView = null
    }

    private fun getThumbColor(context: Context, dragging: Boolean): Int {
        val isLight = ThemeUtils.isLightUiTheme(context)
        return if (isLight) {
            if (dragging) {
                // Dragging: focused charcoal slate
                Color.argb(0xDE, 0x30, 0x30, 0x30)
            } else {
                // Active scroll highlight: soft legible neutral grey
                Color.argb(0xB8, 0x50, 0x50, 0x50)
            }
        } else {
            if (dragging) {
                Color.argb(0xEB, 0xF0, 0xF0, 0xF0)
            } else {
                Color.argb(0xB8, 0xC4, 0xC4, 0xC4)
            }
        }
    }

    private fun recomputeThumbGeometry(parent: RecyclerView, range: Int, extent: Int) {
        val rvHeight = parent.height
        val availableHeight = rvHeight - topMargin - bottomMargin
        if (availableHeight <= 0) {
            thumbHeight = 0
            return
        }
        val maxThumbHeight = maxOf(minThumbHeight, rvHeight / 3).coerceAtMost(availableHeight)
        val minThumb = minOf(minThumbHeight, maxThumbHeight)
        val calculatedHeight = if (range > 0) {
            (extent.toLong() * extent / range).toInt()
        } else {
            minThumb
        }
        thumbHeight = calculatedHeight.coerceIn(minThumb, maxThumbHeight)
    }

    private fun updatePositionFromScroll(rv: RecyclerView) {
        val range = rv.computeVerticalScrollRange()
        val extent = rv.computeVerticalScrollExtent()
        val offset = rv.computeVerticalScrollOffset()
        val maxScroll = range - extent
        if (maxScroll <= 0 || rv.height <= 0) {
            if (isDragging) {
                endDrag(rv)
            }
            if (animAlpha > 0f) {
                fadeOut()
            }
            return
        }
        recomputeThumbGeometry(rv, range, extent)
        val minTop = topMargin
        val maxTop = rv.height - bottomMargin - thumbHeight
        val travel = maxTop - minTop
        if (travel > 0) {
            val fraction = (offset.toFloat() / maxScroll).coerceIn(0f, 1f)
            thumbTop = minTop + (fraction * travel).roundToInt()
        }
        rv.invalidate()
    }

    private fun drawScroller(c: Canvas, parent: RecyclerView) {
        val range = parent.computeVerticalScrollRange()
        val extent = parent.computeVerticalScrollExtent()
        if (range <= extent || parent.height <= 0) return
        if (animAlpha <= 0f) return

        recomputeThumbGeometry(parent, range, extent)

        if (!isDragging) {
            val offset = parent.computeVerticalScrollOffset()
            val maxScroll = range - extent
            val minTop = topMargin
            val maxTop = parent.height - bottomMargin - thumbHeight
            val travel = maxTop - minTop
            if (travel > 0 && maxScroll > 0) {
                val fraction = (offset.toFloat() / maxScroll).coerceIn(0f, 1f)
                thumbTop = minTop + (fraction * travel).roundToInt()
            }
        }

        val isRtl = parent.layoutDirection == View.LAYOUT_DIRECTION_RTL
        val rvWidth = parent.width.toFloat()

        val left: Float
        val right: Float
        if (isRtl) {
            left = pillRightMargin
            right = pillRightMargin + pillWidth
        } else {
            right = rvWidth - pillRightMargin
            left = right - pillWidth
        }

        thumbRect.set(left, thumbTop.toFloat(), right, (thumbTop + thumbHeight).toFloat())

        val color = getThumbColor(parent.context, isDragging)
        val baseAlpha = Color.alpha(color)
        val finalAlpha = (baseAlpha * animAlpha).roundToInt().coerceIn(0, 255)
        thumbPaint.color = color
        thumbPaint.alpha = finalAlpha

        c.drawRoundRect(thumbRect, pillCornerRadius, pillCornerRadius, thumbPaint)
    }

    private fun isPointInsideThumb(rv: RecyclerView, x: Float, y: Float): Boolean {
        if (animAlpha <= 0.05f || thumbHeight <= 0) {
            return false
        }
        val range = rv.computeVerticalScrollRange()
        val extent = rv.computeVerticalScrollExtent()
        if (range <= extent) {
            return false
        }
        val isRtl = rv.layoutDirection == View.LAYOUT_DIRECTION_RTL
        val rvWidth = rv.width.toFloat()

        val touchLeft = if (isRtl) 0f else rvWidth - touchWidth
        val touchRight = if (isRtl) touchWidth else rvWidth
        val touchTop = thumbTop - touchVerticalPadding
        val touchBottom = thumbTop + thumbHeight + touchVerticalPadding

        return x in touchLeft..touchRight && y in touchTop..touchBottom
    }

    private fun handleInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (isPointInsideThumb(rv, e.x, e.y)) {
                    isDragging = true
                    activePointerId = e.getPointerId(0)
                    dragStartY = e.y
                    dragStartThumbTop = thumbTop
                    rv.parent?.requestDisallowInterceptTouchEvent(true)
                    showThumb(immediate = true)
                    rv.invalidate()
                    return true
                } else {
                    // Finger touched screen anywhere: highlight scroller if list is scrollable
                    val range = rv.computeVerticalScrollRange()
                    val extent = rv.computeVerticalScrollExtent()
                    if (range > extent) {
                        updatePositionFromScroll(rv)
                        showThumb()
                        scheduleHide(HIDE_DELAY_MS)
                    }
                }
            }
        }
        return false
    }

    private fun handleTouchEvent(rv: RecyclerView, e: MotionEvent) {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (isPointInsideThumb(rv, e.x, e.y)) {
                    isDragging = true
                    activePointerId = e.getPointerId(0)
                    dragStartY = e.y
                    dragStartThumbTop = thumbTop
                    rv.parent?.requestDisallowInterceptTouchEvent(true)
                    showThumb(immediate = true)
                    rv.invalidate()
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDragging) {
                    val pointerIndex = e.findPointerIndex(activePointerId)
                    if (pointerIndex < 0) return
                    val currentY = e.getY(pointerIndex)
                    val deltaY = currentY - dragStartY
                    val minTop = topMargin
                    val maxTop = rv.height - bottomMargin - thumbHeight
                    val travel = maxTop - minTop
                    if (travel > 0) {
                        val newThumbTop = (dragStartThumbTop + deltaY).coerceIn(minTop.toFloat(), maxTop.toFloat())
                        val scrollFraction = (newThumbTop - minTop) / travel.toFloat()
                        val maxScroll = rv.computeVerticalScrollRange() - rv.computeVerticalScrollExtent()
                        if (maxScroll > 0) {
                            val targetOffset = (scrollFraction * maxScroll).roundToInt()
                            val currentOffset = rv.computeVerticalScrollOffset()
                            val deltaScroll = targetOffset - currentOffset
                            if (deltaScroll != 0) {
                                rv.scrollBy(0, deltaScroll)
                            }
                        }
                        thumbTop = newThumbTop.roundToInt()
                        rv.invalidate()
                    }
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val actionIndex = e.actionIndex
                if (e.getPointerId(actionIndex) == activePointerId) {
                    val newPointerIndex = if (actionIndex == 0) 1 else 0
                    if (newPointerIndex < e.pointerCount) {
                        activePointerId = e.getPointerId(newPointerIndex)
                        dragStartY = e.getY(newPointerIndex)
                        dragStartThumbTop = thumbTop
                    } else {
                        endDrag(rv)
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                endDrag(rv)
            }
        }
    }

    private fun endDrag(rv: RecyclerView) {
        if (isDragging) {
            isDragging = false
            activePointerId = MotionEvent.INVALID_POINTER_ID
            rv.parent?.requestDisallowInterceptTouchEvent(false)
            scheduleHide(HIDE_DELAY_MS)
            rv.invalidate()
        }
    }

    private fun showThumb(immediate: Boolean = false) {
        cancelHide()
        if (immediate) {
            fadeAnimator?.cancel()
            animAlpha = 1.0f
            recyclerView?.invalidate()
            return
        }
        if (animAlpha >= 1.0f) {
            return
        }
        fadeAnimator?.cancel()
        fadeAnimator = ValueAnimator.ofFloat(animAlpha, 1.0f).apply {
            duration = SHOW_DURATION_MS
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animation ->
                animAlpha = animation.animatedValue as Float
                recyclerView?.invalidate()
            }
            start()
        }
    }

    private fun fadeOut() {
        if (isDragging || animAlpha <= 0f) {
            return
        }
        fadeAnimator?.cancel()
        fadeAnimator = ValueAnimator.ofFloat(animAlpha, 0.0f).apply {
            duration = HIDE_DURATION_MS
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animation ->
                animAlpha = animation.animatedValue as Float
                recyclerView?.invalidate()
            }
            start()
        }
    }

    private fun scheduleHide(delayMs: Long) {
        cancelHide()
        recyclerView?.postDelayed(hideRunnable, delayMs)
    }

    private fun cancelHide() {
        recyclerView?.removeCallbacks(hideRunnable)
    }

    companion object {
        private const val HIDE_DELAY_MS = 1500L
        private const val SHOW_DURATION_MS = 100L
        private const val HIDE_DURATION_MS = 250L
    }
}
