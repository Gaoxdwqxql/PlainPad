package com.sunwenqing.plainpad

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.widget.OverScroller

class LargeTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var textData: CharSequence = ""
    private var lineOffsets: IntArray = IntArray(0)
    private var lineCount = 0

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = 36f
        typeface = Typeface.MONOSPACE
    }

    private var lineHeight = 0f
    private var contentHeight = 0
    private var scrollYValue = 0
    private var maxScrollY = 0

    private val scroller = OverScroller(context)
    private var velocityTracker: VelocityTracker? = null
    private val minFlingVelocity = ViewConfiguration.get(context).scaledMinimumFlingVelocity
    private val maxFlingVelocity = ViewConfiguration.get(context).scaledMaximumFlingVelocity
    private var lastTouchY = 0f
    private var isDragging = false
    /** 滑动位移的浮点累加，避免小幅移动被取整吃掉造成"粘连" */
    private var dragAccum = 0f

    /** 字号变化前记录锚点，等尺寸生效后再恢复，避免视口跳动 */
    private var pendingScrollY: Int? = null

    private var onScrollListener: (() -> Unit)? = null
    var onReady: (() -> Unit)? = null

    /** 双指缩放：回调缩放倍数，由外部换算成字号 */
    var onZoom: ((Float) -> Unit)? = null
    var onZoomEnd: (() -> Unit)? = null

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                onZoom?.invoke(detector.scaleFactor)
                return true
            }

            override fun onScaleEnd(detector: ScaleGestureDetector) {
                onZoomEnd?.invoke()
            }
        }
    )

    init {
        isClickable = true
        updateLineHeight()
    }

    fun setOnScrollListener(listener: () -> Unit) {
        this.onScrollListener = listener
    }

    fun setText(text: CharSequence) {
        textData = text
        val len = text.length
        if (len == 0) {
            lineOffsets = IntArray(0)
            lineCount = 0
            contentHeight = 0
            post {
                updateScrollBounds()
                invalidate()
                onReady?.invoke()
            }
            return
        }

        // 后台线程计算换行，避免主线程阻塞
        Thread {
            val starts = ArrayList<Int>(1000)
            starts.add(0)
            var currentLineLength = 0
            val maxLineLength = 5000

            for (i in 0 until len) {
                val c = text[i]
                currentLineLength++
                if (c == '\n' || currentLineLength >= maxLineLength) {
                    starts.add(i + 1)
                    currentLineLength = 0
                }
            }

            val newStarts = starts.toIntArray()

            post {
                lineOffsets = newStarts
                lineCount = lineOffsets.size
                contentHeight = (lineCount * lineHeight).toInt()
                maxScrollY = (contentHeight - height).coerceAtLeast(0)
                // 行偏移算完之前调用过 scrollToY 的话，在这里补上
                pendingScrollY?.let { target ->
                    scrollYValue = target.coerceIn(0, maxScrollY)
                    pendingScrollY = null
                }
                if (scrollYValue > maxScrollY) scrollYValue = maxScrollY
                invalidate()
                onScrollListener?.invoke()
                onReady?.invoke() // 通知主线程渲染完毕，可以隐藏加载框
            }
        }.start()
    }

    fun setTextAppearance(typeface: Typeface, textSizePx: Float, textColor: Int) {
        val anchorLine = if (lineHeight > 0f) scrollYValue / lineHeight else 0f
        paint.typeface = typeface
        paint.textSize = textSizePx
        paint.color = textColor
        updateLineHeight()
        contentHeight = (lineCount * lineHeight).toInt()
        post {
            maxScrollY = (contentHeight - height).coerceAtLeast(0)
            scrollYValue = (anchorLine * lineHeight).toInt().coerceIn(0, maxScrollY)
            invalidate()
            onScrollListener?.invoke()
        }
    }

    /**
     * 只改字号，并保持顶部可见的那一行不动（双指缩放用）。
     * 直接改 paint.textSize 会让视口在缩放时上下乱跳。
     */
    fun setTextSizePx(textSizePx: Float) {
        if (textSizePx <= 0f) return
        val anchorLine = if (lineHeight > 0f) scrollYValue / lineHeight else 0f
        paint.textSize = textSizePx
        updateLineHeight()
        contentHeight = (lineCount * lineHeight).toInt()
        maxScrollY = (contentHeight - height).coerceAtLeast(0)
        scrollYValue = (anchorLine * lineHeight).toInt().coerceIn(0, maxScrollY)
        invalidate()
        onScrollListener?.invoke()
    }

    private fun updateLineHeight() {
        val fm = paint.fontMetrics
        lineHeight = fm.descent - fm.ascent + 4f
    }

    private fun updateScrollBounds() {
        maxScrollY = (contentHeight - height).coerceAtLeast(0)
        if (scrollYValue > maxScrollY) {
            scrollYValue = maxScrollY
            invalidate()
        }
        onScrollListener?.invoke()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateScrollBounds()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (lineCount == 0 || lineHeight <= 0f) return

        val paddingTop = paddingTop
        val startLine = ((scrollYValue - paddingTop) / lineHeight).toInt().coerceAtLeast(0)
        val endLine = ((scrollYValue + height - paddingTop) / lineHeight).toInt().coerceAtMost(lineCount - 1)

        var y = paddingTop + startLine * lineHeight - scrollYValue
        val fm = paint.fontMetrics
        val left = paddingLeft.toFloat()

        for (i in startLine..endLine) {
            val start = lineOffsets[i]
            val end = if (i == lineCount - 1) textData.length else lineOffsets[i + 1] - 1

            if (end > start) {
                val baselineY = y - fm.ascent
                // 直接传 CharSequence 的 offset 绘制
                canvas.drawText(textData, start, end, left, baselineY, paint)
            }
            y += lineHeight
        }
    }

    override fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            scrollYValue = scroller.currY
            invalidate()
            onScrollListener?.invoke()
            postInvalidateOnAnimation()
        }
    }

    fun scrollToY(y: Int) {
        // 行偏移还没算完时先记下来，等 setText 的后台结果回来再定位
        if (lineCount == 0 || contentHeight == 0) {
            pendingScrollY = y
            return
        }
        scrollYValue = y.coerceIn(0, maxScrollY)
        invalidate()
        onScrollListener?.invoke()
    }

    fun getContentHeight(): Int = contentHeight
    fun getCurrentScrollY(): Int = scrollYValue
    fun getMaxScrollY(): Int = maxScrollY
    fun getVisibleHeight(): Int = height

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // 缩放检测器需要看到完整事件流，先喂给它
        scaleDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_POINTER_DOWN -> {
                // 第二根手指落下：停掉滚动和惯性，交给缩放
                isDragging = false
                dragAccum = 0f
                scroller.forceFinished(true)
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_POINTER_UP -> {
                // 缩放结束，剩余手指不再拖动，避免松手时位置跳一下
                isDragging = false
                dragAccum = 0f
                return true
            }
        }

        if (scaleDetector.isInProgress) return true

        if (maxScrollY <= 0) return false

        if (velocityTracker == null) {
            velocityTracker = VelocityTracker.obtain()
        }
        velocityTracker?.addMovement(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                scroller.forceFinished(true)
                lastTouchY = event.y
                dragAccum = 0f
                isDragging = true
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDragging) {
                    dragAccum += lastTouchY - event.y
                    lastTouchY = event.y
                    val step = dragAccum.toInt()
                    if (step != 0) {
                        val target = (scrollYValue + step).coerceIn(0, maxScrollY)
                        // 已经到边界就把余量丢掉，否则反向时会先"空转"一段
                        if (target == scrollYValue) dragAccum = 0f else dragAccum -= step
                        scrollYValue = target
                        invalidate()
                        onScrollListener?.invoke()
                    }
                    return true
                }
            }
            MotionEvent.ACTION_UP -> {
                isDragging = false
                dragAccum = 0f
                parent?.requestDisallowInterceptTouchEvent(false)

                velocityTracker?.computeCurrentVelocity(1000, maxFlingVelocity.toFloat())
                val vy = velocityTracker?.yVelocity ?: 0f

                if (Math.abs(vy) > minFlingVelocity) {
                    scroller.fling(0, scrollYValue, 0, (-vy).toInt(), 0, 0, 0, maxScrollY)
                    invalidate()
                }
                velocityTracker?.clear()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                isDragging = false
                dragAccum = 0f
                parent?.requestDisallowInterceptTouchEvent(false)
                velocityTracker?.clear()
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}