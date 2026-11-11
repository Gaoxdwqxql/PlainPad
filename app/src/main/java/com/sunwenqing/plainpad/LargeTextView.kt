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
    /** 未过滤的原始文本；切换 Zalgo 设置时用它重新生成显示文本 */
    private var rawTextData: CharSequence = ""
    private var lineOffsets: IntArray = IntArray(0)
    private var lineCount = 0
    /** 防止旧的 setText 后台线程覆盖新文档 */
    private var textGeneration = 0

    /** 是否允许堆叠组合字符；关闭时只限制显示，不改写文件内容 */
    var allowZalgo: Boolean = true
        set(value) {
            if (field == value) return
            field = value
            windowFrom = -1
            windowTo = -1
            windowLines = null
            if (chunkedSource == null && rawTextData.isNotEmpty()) {
                val y = scrollYValue
                setText(rawTextData)
                pendingScrollY = y
            } else {
                invalidate()
            }
        }

    /** 非空时表示走「按需读取」模式：不在内存里保留全文 */
    private var chunkedSource: ChunkedTextSource? = null

    /** 最近一次读取的行窗口，区间没变时直接复用 */
    private var windowFrom = -1
    private var windowTo = -1
    private var windowLines: List<String>? = null
    private var windowBytes = 0
    private var windowReadMs = 0L
    private var windowCached = false

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

    /** 墨水屏优化：关掉惯性滑动，减少残影 */
    var allowFling: Boolean = true
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
        val generation = ++textGeneration
        chunkedSource = null
        rawTextData = text
        windowFrom = -1
        windowTo = -1
        windowLines = null
        windowBytes = 0
        windowReadMs = 0L
        windowCached = false
        textData = text
        // 先把旧的行索引清掉：后台算新索引期间 onDraw 可能被触发，
        // 如果 lineCount 还是上一篇文档的值，lineOffsets 却已经清空就会越界。
        lineOffsets = IntArray(0)
        lineCount = 0
        contentHeight = 0
        maxScrollY = 0
        pendingScrollY = null
        invalidate()
        val len = text.length
        if (len == 0) {
            lineOffsets = IntArray(0)
            lineCount = 0
            contentHeight = 0
            post {
                if (generation != textGeneration) return@post
                updateScrollBounds()
                invalidate()
                onReady?.invoke()
            }
            return
        }

        // 后台线程计算换行，避免主线程阻塞
        Thread {
            val display = if (allowZalgo) text else ZalgoText.limit(text)
            val len = display.length
            val starts = ArrayList<Int>(1000)
            starts.add(0)
            var currentLineLength = 0
            val maxLineLength = MAX_VISUAL_LINE_CHARS

            for (i in 0 until len) {
                val c = display[i]
                currentLineLength++
                if (c == '\n' || currentLineLength >= maxLineLength) {
                    starts.add(i + 1)
                    currentLineLength = 0
                }
            }

            val newStarts = starts.toIntArray()

            post {
                if (generation != textGeneration) return@post
                textData = display
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

    /**
     * 按需读取模式：只记录行数，绘制时按可视行号向 [ChunkedTextSource] 取内容。
     * 内存占用与文件大小无关，这是超大文件能打开的关键。
     */
    fun setChunkedSource(source: ChunkedTextSource) {
        textGeneration++
        textData = ""
        rawTextData = ""
        lineOffsets = IntArray(0)
        chunkedSource = source
        windowFrom = -1
        windowTo = -1
        windowLines = null
        windowBytes = 0
        windowReadMs = 0L
        windowCached = false

        lineCount = source.lineCount
        contentHeight = (lineCount * lineHeight).toInt()
        // 同步算一次上限：否则紧接着的 scrollToY 会被旧的 0 夹住，恢复不了阅读位置
        maxScrollY = (contentHeight - height).coerceAtLeast(0)
        post {
            maxScrollY = (contentHeight - height).coerceAtLeast(0)
            pendingScrollY?.let { target ->
                scrollYValue = target.coerceIn(0, maxScrollY)
                pendingScrollY = null
            }
            if (scrollYValue > maxScrollY) scrollYValue = maxScrollY
            invalidate()
            onScrollListener?.invoke()
            onReady?.invoke()
        }
    }

    /** 行数（两种模式通用），供跳转行、统计使用 */
    fun getLineCount(): Int = lineCount

    /** 当前视口顶部第一条可见行（0 基），沉浸阅读进度用 */
    fun getFirstVisibleLine(): Int {
        if (lineHeight <= 0f) return 0
        return ((scrollYValue - paddingTop) / lineHeight).toInt().coerceAtLeast(0)
    }

    /** 只改文字颜色（墨水屏切换用） */
    fun setTextColor(color: Int) {
        paint.color = color
        invalidate()
    }

    /** 墨水屏优化：按需绘制的文字也能加粗 */
    fun setFakeBold(enabled: Boolean) {
        if (paint.isFakeBoldText == enabled) return
        paint.isFakeBoldText = enabled
        invalidate()
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
        // 取整：小数行高会让每帧基线落在亚像素上，滑动时笔画轻微抖动
        lineHeight = kotlin.math.ceil(fm.descent - fm.ascent + 4f)
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

        val frameStartedNs = System.nanoTime()
        val paddingTop = paddingTop
        val startLine = ((scrollYValue - paddingTop) / lineHeight).toInt().coerceAtLeast(0)
        val endLine = ((scrollYValue + height - paddingTop) / lineHeight).toInt().coerceAtMost(lineCount - 1)

        var y = paddingTop + startLine * lineHeight - scrollYValue
        val fm = paint.fontMetrics
        val left = paddingLeft.toFloat()

        val source = chunkedSource
        // 内存模式下行索引还没算完（或已被释放）时不绘制，避免读空数组
        if (source == null && (lineCount <= 0 || lineOffsets.size < lineCount)) return
        // 一帧只读一次连续字节；区间没变（例如仅重绘）就复用上次结果，避免重复 IO
        val visibleLines: List<String>? = if (source != null) {
            if (startLine != windowFrom || endLine != windowTo || windowLines == null) {
                val raw = runCatching { source.readLines(startLine, endLine) }.getOrNull()
                windowLines = if (allowZalgo) raw
                else raw?.map { ZalgoText.limit(it).toString() }
                windowFrom = startLine
                windowTo = endLine
                windowBytes = source.lastReadBytes
                windowReadMs = source.lastReadMs
                windowCached = false
            } else {
                windowCached = true
            }
            windowLines
        } else {
            null
        }

        for (i in startLine..endLine) {
            if (source != null) {
                val index = i - startLine
                val line = if (visibleLines != null && index in visibleLines.indices) {
                    visibleLines[index]
                } else {
                    ""
                }
                if (line.isNotEmpty()) {
                    // 基线取整：ascent 是小数，取整后每帧落点一致，拖动时不会抖
                    canvas.drawText(
                        line, 0, line.length, left,
                        kotlin.math.round(y - fm.ascent), paint
                    )
                }
            } else {
                val start = lineOffsets[i]
                val rawEnd = if (i == lineCount - 1) textData.length else lineOffsets[i + 1]
                var end = rawEnd
                // 普通行以换行结尾；超长行是在 1000 字符处硬切的，末尾没有换行，
                // 不能像旧代码那样无条件减 1，否则会把每段最后一个字符吃掉
                if (end > start && textData[end - 1] == '\n') end--
                if (end > start && textData[end - 1] == '\r') end--

                if (end > start) {
                    val baselineY = kotlin.math.round(y - fm.ascent)
                    // 直接传 CharSequence 的 offset 绘制
                    canvas.drawText(textData, start, end, left, baselineY, paint)
                }
            }
            y += lineHeight
        }

        DevDiag.recordFrame(
            totalLines = lineCount,
            chunked = source != null,
            from = startLine,
            to = endLine,
            bytes = windowBytes,
            ms = windowReadMs,
            cached = windowCached,
            frameMs = (System.nanoTime() - frameStartedNs) / 1_000_000
        )
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

    /** 关闭标签或切换文档时释放数据源，避免文件句柄泄漏 */
    fun releaseChunkedSource() {
        textGeneration++
        chunkedSource?.close()
        chunkedSource = null
        textData = ""
        rawTextData = ""
        lineOffsets = IntArray(0)
        lineCount = 0
        contentHeight = 0
        maxScrollY = 0
        windowFrom = -1
        windowTo = -1
        windowLines = null
        windowBytes = 0
        windowReadMs = 0L
        windowCached = false
        invalidate()
    }

    private companion object {
        /**
         * 内存模式下单个显示行的最大字符数。20MB 单行文本如果按 5000 字符切，
         * 每帧要画十几万字符；切到 1000 后只画可视区域，长行不再拖慢滚动。
         */
        const val MAX_VISUAL_LINE_CHARS = 1000
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {        // 缩放检测器需要看到完整事件流，先喂给它
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

                if (allowFling && Math.abs(vy) > minFlingVelocity) {
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