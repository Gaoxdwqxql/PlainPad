package com.sunwenqing.plainpad

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.MotionEvent
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

    private var onScrollListener: (() -> Unit)? = null
    var onReady: (() -> Unit)? = null

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
                updateScrollBounds()
                invalidate()
                onReady?.invoke() // 通知主线程渲染完毕，可以隐藏加载框
            }
        }.start()
    }

    fun setTextAppearance(typeface: Typeface, textSizePx: Float, textColor: Int) {
        paint.typeface = typeface
        paint.textSize = textSizePx
        paint.color = textColor
        updateLineHeight()
        contentHeight = (lineCount * lineHeight).toInt()
        post { updateScrollBounds() }
        invalidate()
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
        scrollYValue = y.coerceIn(0, maxScrollY)
        invalidate()
        onScrollListener?.invoke()
    }

    fun getContentHeight(): Int = contentHeight
    fun getCurrentScrollY(): Int = scrollYValue
    fun getMaxScrollY(): Int = maxScrollY
    fun getVisibleHeight(): Int = height

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (maxScrollY <= 0) return false

        if (velocityTracker == null) {
            velocityTracker = VelocityTracker.obtain()
        }
        velocityTracker?.addMovement(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                scroller.forceFinished(true)
                lastTouchY = event.y
                isDragging = true
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDragging) {
                    val dy = lastTouchY - event.y
                    scrollYValue = (scrollYValue + dy.toInt()).coerceIn(0, maxScrollY)
                    lastTouchY = event.y
                    invalidate()
                    onScrollListener?.invoke()
                    return true
                }
            }
            MotionEvent.ACTION_UP -> {
                isDragging = false
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
                parent?.requestDisallowInterceptTouchEvent(false)
                velocityTracker?.clear()
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}