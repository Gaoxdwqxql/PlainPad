package com.sunwenqing.plainpad

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Editable
import android.text.TextPaint
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.View
import android.widget.EditText
import kotlin.math.max

/**
 * 行号 View：
 * 直接读取 EditText 的 Layout，逐行取 baseline 精确绘制。
 *
 * 宽度策略（关键修复）：
 * - 宽度根据当前字号，使用 paint.measureText 精确测量，绝不让数字溢出左边缘。
 * - 只有行号位数变化、或字号/字体变化时，才重新计算宽度并 requestLayout。
 * - 这样双指缩放字号时，行号栏宽度会自动适配，编辑区左边缘依然稳定。
 */
class LineNumberView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var editor: EditText? = null

    private val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.RIGHT
        color = Color.GRAY
    }

    /** 锁定的宽度（px）。只有行号位数或字号变化时才更新 */
    private var lockedWidth = 0
    /** 上次计算宽度时使用的行号位数 */
    private var lastDigits = -1

    // ---------------- 对外接口 ----------------

    /** 文字颜色（编辑区主题切换时调用） */
    fun setLineNumberTextColor(color: Int) {
        paint.color = color
        invalidate()
    }

    /**
     * 字体同步（字号 / 字体族变化时调用）。
     * 关键：字号改变时，必须重新计算宽度并 requestLayout，
     * 否则字变大了，行号栏宽度不够，数字会超出屏幕边缘。
     */
    fun setTypefaceAndSize(tf: Typeface, sizePx: Float) {
        paint.typeface = tf
        paint.textSize = sizePx

        // ★ 字号变化后，重新计算所需宽度
        val lineCount = editor?.lineCount ?: 1
        val digits = max(lineCount, 1).toString().length.coerceAtLeast(3)
        val newWidth = computeDesiredWidth(digits)
        if (lockedWidth != newWidth) {
            lockedWidth = newWidth
            requestLayout()
        }
        invalidate()
    }

    /** 绑定编辑器，自动跟随内容与滚动刷新 */
    fun attach(editText: EditText) {
        editor = editText

        editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updateWidthIfNeeded()
                invalidate()
            }
        })

        // 编辑器滚动时行号随之重绘
        editText.viewTreeObserver.addOnScrollChangedListener { invalidate() }

        // 布局变化时只检查"行号位数"是否变化，不盲目 requestLayout
        editText.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            updateWidthIfNeeded()
            invalidate()
        }

        // 首帧初始化宽度
        editText.post { updateWidthIfNeeded() }
    }

    // ---------------- 宽度计算 ----------------

    /**
     * 只在"行号位数"变化时才更新 lockedWidth，
     * 其余情况（滚动、光标移动）一律不重测宽度。
     */
    private fun updateWidthIfNeeded() {
        val lineCount = editor?.lineCount ?: 1
        val digits = max(lineCount, 1).toString().length.coerceAtLeast(3)
        if (digits == lastDigits && lockedWidth > 0) return

        lastDigits = digits
        lockedWidth = computeDesiredWidth(digits)
        requestLayout()
    }

    private fun computeDesiredWidth(digits: Int): Int {
        val density = resources.displayMetrics.density

        // ★ 使用当前 paint 的 measureText 动态测量数字宽度，而不是固定 dp
        val textArea = paint.measureText("8".repeat(digits))

        // 数字右边缘到行号栏右边缘的间距（dp）
        val endGapDp = 6f
        // 行号栏左边缘到数字左边缘的间距（dp）
        val startGapDp = 4f

        val endGap = endGapDp * density
        val startGap = startGapDp * density

        val desired = (textArea + startGap + endGap).toInt()
        val minWidth = (24 * density).toInt()
        return max(minWidth, desired)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val height = MeasureSpec.getSize(heightMeasureSpec)

        // 首次进入时若 lockedWidth 还未计算，立即算一次
        if (lockedWidth <= 0) {
            val lineCount = editor?.lineCount ?: 1
            val digits = max(lineCount, 1).toString().length.coerceAtLeast(3)
            lastDigits = digits
            lockedWidth = computeDesiredWidth(digits)
        }

        setMeasuredDimension(lockedWidth, height)
    }

    // ---------------- 绘制 ----------------

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val ed = editor ?: return
        val layout = ed.layout ?: return
        val editable = ed.text ?: return

        val paddingTop = ed.paddingTop
        val scrollY = ed.scrollY
        // 数字右边缘固定贴行号栏右侧（减去 paddingEnd）
        val right = (width - paddingEnd).toFloat()

        val visualCount = layout.lineCount
        var logicalNo = 1

        for (i in 0 until visualCount) {
            val lineStart = layout.getLineStart(i)
            val isLogicalStart = lineStart == 0 ||
                    (lineStart > 0 && editable[lineStart - 1] == '\n')

            if (isLogicalStart) {
                val baselineY =
                    (paddingTop + layout.getLineBaseline(i) - scrollY).toFloat()

                // 只绘制可见范围内的数字
                if (baselineY >= -paint.textSize && baselineY <= height + paint.textSize) {
                    canvas.drawText(logicalNo.toString(), right, baselineY, paint)
                }
                logicalNo++
            }
        }
    }
}