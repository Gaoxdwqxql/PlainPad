package com.sunwenqing.plainpad

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.widget.EditText
import android.widget.OverScroller
import android.widget.ScrollView
import com.google.android.material.color.MaterialColors
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 通用滚动条：
 * - 支持 EditText（编辑器）与 ScrollView（设置页等）
 * - 拖动时通过 scrollTo 控制目标滚动位置
 * - 松手后按手指速度惯性滑动（OverScroller）
 * - 触摸时立即打断惯性
 * - 滚动或触摸时淡入，1.2 秒无操作后淡出
 * - 颜色跟随主题
 * - 内容不足以滚动时自动隐藏
 */
class FastScrollBarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var editor: EditText? = null
    private var scrollView: ScrollView? = null

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val density = resources.displayMetrics.density
    private val thumbMinHeight = 40 * density

    private var thumbTop = 0f
    private var thumbHeight = 0f
    private var isDragging = false
    private var dragOffsetY = 0f

    private val hideHandler = Handler(Looper.getMainLooper())
    private var hideRunnable: Runnable? = null

    private val scroller = OverScroller(context)
    private var velocityTracker: VelocityTracker? = null
    private val minFlingVelocity =
        ViewConfiguration.get(context).scaledMinimumFlingVelocity
    private val maxFlingVelocity =
        ViewConfiguration.get(context).scaledMaximumFlingVelocity
    private var isFlinging = false

    init {
        isClickable = true
        alpha = 0f
        trackPaint.color = 0x22000000
        thumbPaint.color = 0x88000000.toInt()
    }

    // ---------------- 绑定 ----------------

    fun attach(editText: EditText) {
        editor = editText
        scrollView = null
        syncColorFromEditor()

        editText.viewTreeObserver.addOnScrollChangedListener {
            if (isDragging || isFlinging) return@addOnScrollChangedListener
            updateThumb()
            flashVisible()
        }

        editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                post { updateThumb() }
            }
        })

        editText.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            post { updateThumb() }
        }

        post { updateThumb() }
    }

    fun attachScrollView(sv: ScrollView) {
        scrollView = sv
        editor = null

        // 颜色从主题推导
        val rgb = MaterialColors.getColor(
            sv, com.google.android.material.R.attr.colorOnSurface
        ) and 0x00FFFFFF
        thumbPaint.color = rgb or 0x66000000
        trackPaint.color = rgb or 0x22000000

        sv.viewTreeObserver.addOnScrollChangedListener {
            if (isDragging || isFlinging) return@addOnScrollChangedListener
            updateThumb()
            flashVisible()
        }

        sv.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            post { updateThumb() }
        }

        post { updateThumb() }
    }

    /** 用编辑器当前文字色推导滚动条颜色 */
    fun syncColorFromEditor() {
        val ed = editor ?: return
        val rgb = ed.currentTextColor and 0x00FFFFFF
        thumbPaint.color = rgb or 0x66000000
        trackPaint.color = rgb or 0x22000000
        invalidate()
    }

    // ---------------- 尺寸计算 ----------------

    private fun contentHeight(): Float {
        editor?.let { return (it.layout?.height ?: 0).toFloat() }
        scrollView?.let { return (it.getChildAt(0)?.height ?: 0).toFloat() }
        return 0f
    }

    private fun visibleHeight(): Float {
        editor?.let { return (it.height - it.paddingTop - it.paddingBottom).toFloat() }
        scrollView?.let { return it.height.toFloat() }
        return 0f
    }

    private fun currentScrollY(): Int {
        editor?.let { return it.scrollY }
        scrollView?.let { return it.scrollY }
        return 0
    }

    private fun applyScroll(y: Int) {
        editor?.scrollTo(0, y)
        scrollView?.scrollTo(0, y)
    }

    private fun maxScrollY(): Float =
        (contentHeight() - visibleHeight()).coerceAtLeast(0f)

    private fun updateThumb() {
        val viewHeight = height.toFloat()
        if (viewHeight <= 0f) return

        val contentH = contentHeight()
        val visibleH = visibleHeight()

        if (contentH <= visibleH || contentH <= 0f) {
            thumbTop = 0f
            thumbHeight = viewHeight
            invalidate()
            return
        }

        thumbHeight = max(thumbMinHeight, viewHeight * visibleH / contentH)
        thumbHeight = min(thumbHeight, viewHeight)

        val maxS = maxScrollY()
        val scrollY = currentScrollY().toFloat().coerceIn(0f, maxS)
        val ratio = if (maxS > 0f) scrollY / maxS else 0f

        thumbTop = (viewHeight - thumbHeight) * ratio.coerceIn(0f, 1f)
        invalidate()
    }

    private fun scrollEditorToThumb() {
        val viewHeight = height.toFloat()
        val trackRange = viewHeight - thumbHeight
        if (trackRange <= 0f) return

        val ratio = (thumbTop / trackRange).coerceIn(0f, 1f)
        val maxS = maxScrollY()
        if (maxS <= 0f) return

        applyScroll((ratio * maxS).toInt())
    }

    // ---------------- 惯性 ----------------

    override fun computeScroll() {
        super.computeScroll()
        if (scroller.computeScrollOffset()) {
            val viewHeight = height.toFloat()
            thumbTop = scroller.currY.toFloat()
                .coerceIn(0f, (viewHeight - thumbHeight).coerceAtLeast(0f))
            scrollEditorToThumb()
            invalidate()
            postInvalidateOnAnimation()
        } else if (isFlinging) {
            isFlinging = false
            hideAfterDelay()
        }
    }

    // ---------------- 绘制 ----------------

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (alpha <= 0f) return
        if (contentHeight() <= visibleHeight()) return

        val w = width.toFloat()
        val trackLeft = w * 0.30f
        val trackRight = w * 0.70f
        val thumbLeft = w * 0.15f
        val thumbRight = w * 0.85f

        if (isDragging || isFlinging) {
            val r = (trackRight - trackLeft) / 2f
            canvas.drawRoundRect(
                trackLeft, 0f, trackRight, height.toFloat(), r, r, trackPaint
            )
        }

        val radius = (thumbRight - thumbLeft) / 2f
        canvas.drawRoundRect(
            thumbLeft, thumbTop, thumbRight, thumbTop + thumbHeight,
            radius, radius, thumbPaint
        )
    }

    // ---------------- 触摸 ----------------

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (editor == null && scrollView == null) return false
        if (contentHeight() <= visibleHeight()) return false

        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            scroller.forceFinished(true)
            isFlinging = false
        }

        if (velocityTracker == null) {
            velocityTracker = VelocityTracker.obtain()
        }
        velocityTracker?.addMovement(event)

        val viewHeight = height.toFloat()

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val y = event.y
                if (y in thumbTop..(thumbTop + thumbHeight)) {
                    dragOffsetY = y - thumbTop
                } else {
                    dragOffsetY = thumbHeight / 2f
                    thumbTop = (y - dragOffsetY).coerceIn(
                        0f, (viewHeight - thumbHeight).coerceAtLeast(0f)
                    )
                    scrollEditorToThumb()
                    invalidate()
                }
                isDragging = true
                cancelHide()
                showImmediate()
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (!isDragging) return false
                val y = event.y
                thumbTop = (y - dragOffsetY).coerceIn(
                    0f, (viewHeight - thumbHeight).coerceAtLeast(0f)
                )
                scrollEditorToThumb()
                invalidate()
                return true
            }

            MotionEvent.ACTION_UP -> {
                isDragging = false
                parent?.requestDisallowInterceptTouchEvent(false)

                velocityTracker?.computeCurrentVelocity(
                    1000, maxFlingVelocity.toFloat()
                )
                val vy = velocityTracker?.yVelocity ?: 0f

                if (abs(vy) > minFlingVelocity) {
                    val startY = thumbTop.toInt()
                    val minY = 0
                    val maxY = (viewHeight - thumbHeight).toInt().coerceAtLeast(0)
                    scroller.fling(0, startY, 0, (-vy).toInt(), 0, 0, minY, maxY)
                    isFlinging = true
                    invalidate()
                } else {
                    hideAfterDelay()
                }

                velocityTracker?.clear()
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                isDragging = false
                parent?.requestDisallowInterceptTouchEvent(false)
                velocityTracker?.clear()
                hideAfterDelay()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    // ---------------- 自动显示 / 隐藏 ----------------

    private fun flashVisible() {
        if (isDragging || isFlinging) return
        showImmediate()
        hideAfterDelay()
    }

    private fun showImmediate() {
        cancelHide()
        animate().cancel()
        alpha = 1f
    }

    private fun hideAfterDelay() {
        cancelHide()
        val r = Runnable {
            animate().cancel()
            animate().alpha(0f).setDuration(350).start()
        }
        hideRunnable = r
        hideHandler.postDelayed(r, 1200)
    }

    private fun cancelHide() {
        hideRunnable?.let { hideHandler.removeCallbacks(it) }
        hideRunnable = null
    }

    override fun onDetachedFromWindow() {
        cancelHide()
        scroller.abortAnimation()
        velocityTracker?.recycle()
        velocityTracker = null
        super.onDetachedFromWindow()
    }
}