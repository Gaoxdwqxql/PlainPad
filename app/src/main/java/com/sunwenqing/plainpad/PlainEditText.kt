package com.sunwenqing.plainpad

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.text.Layout
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedText
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import android.view.textclassifier.TextClassifier
import android.widget.TextView
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat

class PlainEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.appcompat.R.attr.editTextStyle
) : AppCompatEditText(context, attrs, defStyleAttr) {

    @Volatile
    private var allowBring = false

    private val MAX_IME_TEXT_LENGTH = 100_000

    private var lastClassifierNoOp: Boolean? = null

    var isReadOnlyMode = false
        set(value) {
            field = value
            isCursorVisible = !value
            showSoftInputOnFocus = !value
        }

    var isInteractionDisabled = false
        set(value) {
            field = value
            if (value) {
                isFocusable = false
                isFocusableInTouchMode = false
                isLongClickable = false
                setTextIsSelectable(false)
                isCursorVisible = false
            } else {
                isFocusable = true
                isFocusableInTouchMode = true
                isLongClickable = true
                setTextIsSelectable(true)
            }
        }

    /** 当前行高亮开关。关闭时立即移除已经画上去的背景。 */
    var currentLineHighlightEnabled: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            applyCurrentLineHighlight()
        }

    private var currentLineSpan: BackgroundColorSpan? = null

    /** 书签所在的视觉行号（0 基），画在编辑区左侧留白里 */
    private var bookmarkedLines: Set<Int> = emptySet()

    private val bookmarkPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun setBookmarkedLines(lines: Set<Int>) {
        if (bookmarkedLines == lines) return
        bookmarkedLines = lines
        invalidate()
    }

    init {
        disableMagnifier()
        applyTextClassifierByLength()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            breakStrategy = Layout.BREAK_STRATEGY_SIMPLE
            hyphenationFrequency = Layout.HYPHENATION_FREQUENCY_NONE
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        applyThemedCursorAndHandles()
    }

    /** 书签槽（左侧留白）被点击：回调被点的视觉行号 */
    var bookmarkGutterTapListener: ((Int) -> Unit)? = null

    /** 书签槽被长按：打开书签管理面板 */
    var bookmarkGutterLongPressListener: (() -> Unit)? = null

    private val gutterDetector by lazy {
        GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onSingleTapUp(e: MotionEvent): Boolean {
                lineForY(e.y)?.let { bookmarkGutterTapListener?.invoke(it) }
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                bookmarkGutterLongPressListener?.invoke()
            }
        })
    }

    /** 把触摸点的 y 换算成视觉行号（考虑滚动与 padding） */
    private fun lineForY(y: Float): Int? {
        val layout = layout ?: return null
        if (layout.lineCount == 0) return null
        val v = (y + scrollY - paddingTop).toInt()
        if (v <= 0) return 0
        if (v >= layout.height) return layout.lineCount - 1
        return layout.getLineForVertical(v)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (isInteractionDisabled) return true
        // 左侧留白当作书签槽：点一下切换书签，长按开面板
        if (paddingLeft > 0 && event.x < paddingLeft &&
            (bookmarkGutterTapListener != null || bookmarkGutterLongPressListener != null)
        ) {
            gutterDetector.onTouchEvent(event)
            return true
        }
        return super.onTouchEvent(event)
    }

    override fun getDefaultEditable(): Boolean = !isReadOnlyMode

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        val superConnection = super.onCreateInputConnection(outAttrs) ?: return null
        return object : InputConnectionWrapper(superConnection, false) {
            override fun getExtractedText(
                request: ExtractedTextRequest?,
                flags: Int
            ): ExtractedText? {
                if ((text?.length ?: 0) > MAX_IME_TEXT_LENGTH) return null
                return super.getExtractedText(request, flags)
            }

            override fun getTextBeforeCursor(n: Int, flags: Int): CharSequence? =
                super.getTextBeforeCursor(n.coerceAtMost(1000), flags)

            override fun getTextAfterCursor(n: Int, flags: Int): CharSequence? =
                super.getTextAfterCursor(n.coerceAtMost(1000), flags)
        }
    }

    override fun onTextChanged(
        text: CharSequence?,
        start: Int,
        lengthBefore: Int,
        lengthAfter: Int
    ) {
        super.onTextChanged(text, start, lengthBefore, lengthAfter)
        applyTextClassifierByLength()
        if (currentLineHighlightEnabled) post { applyCurrentLineHighlight() }
    }

    /** 光标/选区变化回调，用于同步外部按钮状态 */
    var selectionChangedListener: (() -> Unit)? = null

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        if (!allowBring) {
            post { ensureCursorVisible() }
        }
        post { applyThemedCursorAndHandles() }
        if (currentLineHighlightEnabled) post { applyCurrentLineHighlight() }
        selectionChangedListener?.invoke()
    }

    // ---------------- 当前行高亮 ----------------

    override fun onDraw(canvas: Canvas) {
        drawBookmarkDots(canvas)
        super.onDraw(canvas)
    }

    /** 在左侧 padding 里画书签圆点，不依赖行号栏是否显示 */
    private fun drawBookmarkDots(canvas: Canvas) {
        if (bookmarkedLines.isEmpty()) return
        val layout = layout ?: return
        val density = resources.displayMetrics.density
        val radius = 2.5f * density
        val x = 7f * density
        bookmarkPaint.color = (currentTextColor and 0x00FFFFFF) or 0x99000000.toInt()
        val scroll = scrollY
        val textH = textSize
        for (line in bookmarkedLines) {
            if (line < 0 || line >= layout.lineCount) continue
            val baseline = paddingTop + layout.getLineBaseline(line) - scroll
            if (baseline < -radius || baseline > height + radius) continue
            canvas.drawCircle(x, baseline - textH * 0.35f, radius, bookmarkPaint)
        }
    }

    /** 主题色变化后重新取色 */
    fun refreshCurrentLineHighlight() = applyCurrentLineHighlight()

    private fun applyCurrentLineHighlight() {
        val editable = text ?: return
        currentLineSpan?.let { editable.removeSpan(it) }
        currentLineSpan = null

        if (!currentLineHighlightEnabled) return
        // 有选区时不画，避免和选区高亮叠在一起
        if (selectionStart != selectionEnd) return
        val layout = layout ?: return

        val len = editable.length
        if (len == 0) return
        val cursor = selectionEnd.coerceIn(0, len)
        val line = layout.getLineForOffset(cursor)
        val start = layout.getLineStart(line).coerceIn(0, len)
        val end = layout.getLineEnd(line).coerceIn(start, len)
        if (end <= start) return

        val span = BackgroundColorSpan(lineHighlightColor())
        editable.setSpan(span, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        currentLineSpan = span
    }

    /** 用主题色（colorControlActivated）降低不透明度作背景，保证跟随换色 */
    private fun lineHighlightColor(): Int {
        val base = resolveColor(androidx.appcompat.R.attr.colorControlActivated, 0xFF6750A4.toInt())
        return (base and 0x00FFFFFF) or 0x1F000000
    }

    // ---------------- 主题色 cursor / handle ----------------

    private fun applyThemedCursorAndHandles() {
        val color = resolveColor(
            androidx.appcompat.R.attr.colorControlActivated,
            0xFF6750A4.toInt()
        )

        val cursor = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setSize(dp(2), 0)
            setColor(color)
        }
        setCursorDrawableCompat(cursor)

        val middle = ContextCompat.getDrawable(
            context, androidx.appcompat.R.drawable.abc_text_select_handle_middle_mtrl
        )
        val left = ContextCompat.getDrawable(
            context, androidx.appcompat.R.drawable.abc_text_select_handle_left_mtrl
        )
        val right = ContextCompat.getDrawable(
            context, androidx.appcompat.R.drawable.abc_text_select_handle_right_mtrl
        )

        if (middle != null && left != null && right != null) {
            setSelectHandleCompat(
                tintDrawable(middle.mutate(), color),
                tintDrawable(left.mutate(), color),
                tintDrawable(right.mutate(), color)
            )
        }
    }

    private fun tintDrawable(src: Drawable, color: Int): Drawable {
        val d = DrawableCompat.wrap(src).mutate()
        DrawableCompat.setTint(d, color)
        return d
    }

    @SuppressLint("BlockedPrivateApi")
    private fun setCursorDrawableCompat(d: Drawable) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            textCursorDrawable = d
        } else {
            try {
                val f = TextView::class.java.getDeclaredField("mCursorDrawable")
                f.isAccessible = true
                f.set(this, arrayOf(d, d))
            } catch (_: Exception) {}
        }
    }

    @SuppressLint("BlockedPrivateApi")
    private fun setSelectHandleCompat(middle: Drawable, left: Drawable, right: Drawable) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                setTextSelectHandle(middle)
                setTextSelectHandleLeft(left)
                setTextSelectHandleRight(right)
            } catch (_: Exception) {}
        }
        setHandleField("mTextSelectHandle", middle)
        setHandleField("mTextSelectHandleLeft", left)
        setHandleField("mTextSelectHandleRight", right)
    }

    @SuppressLint("BlockedPrivateApi")
    private fun setHandleField(name: String, d: Drawable) {
        try {
            val f = TextView::class.java.getDeclaredField(name)
            f.isAccessible = true
            f.set(this, d)
        } catch (_: Exception) {}
    }

    private fun resolveColor(attr: Int, fallback: Int): Int {
        val tv = android.util.TypedValue()
        return if (context.theme.resolveAttribute(attr, tv, true)) {
            if (tv.resourceId != 0) ContextCompat.getColor(context, tv.resourceId)
            else tv.data
        } else fallback
    }

    private fun dp(v: Int): Int =
        (v * resources.displayMetrics.density).toInt().coerceAtLeast(1)

    // ---------------- TextClassifier ----------------

    private fun applyTextClassifierByLength() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val len = text?.length ?: 0
        val shouldNoOp = len > MAX_IME_TEXT_LENGTH
        if (lastClassifierNoOp == shouldNoOp) return
        lastClassifierNoOp = shouldNoOp
        try {
            setTextClassifier(if (shouldNoOp) TextClassifier.NO_OP else null)
        } catch (_: Exception) {}
    }

    // ---------------- 放大镜 ----------------

    @SuppressLint("BlockedPrivateApi")
    private fun disableMagnifier() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        try {
            val method = TextView::class.java.getDeclaredMethod(
                "setMagnifierEnabled",
                Boolean::class.javaPrimitiveType
            )
            method.isAccessible = true
            method.invoke(this, false)
        } catch (_: Exception) {}
    }

    // ---------------- 光标可见性 ----------------

    override fun bringPointIntoView(offset: Int): Boolean =
        if (allowBring) super.bringPointIntoView(offset) else false

    private fun ensureCursorVisible() {
        val layout = layout ?: return
        val text = text ?: return
        val cursor = selectionEnd
        if (cursor < 0 || cursor > text.length) return
        if (layout.lineCount == 0) return

        val line = layout.getLineForOffset(cursor)
        val lineTop = layout.getLineTop(line)
        val lineBottom = layout.getLineBottom(line)

        val visibleHeight = height - paddingTop - paddingBottom
        val visibleTop = scrollY
        val visibleBottom = visibleTop + visibleHeight

        when {
            lineTop < visibleTop -> scrollTo(0, lineTop)
            lineBottom > visibleBottom -> scrollTo(0, lineBottom - visibleHeight)
        }
    }

    fun forceBringCursorIntoView() {
        allowBring = true
        try {
            bringPointIntoView(selectionEnd)
        } finally {
            allowBring = false
        }
    }
}