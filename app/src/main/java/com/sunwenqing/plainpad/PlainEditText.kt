package com.sunwenqing.plainpad

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.text.Layout
import android.util.AttributeSet
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

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (isInteractionDisabled) return true
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
    }

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        if (!allowBring) {
            post { ensureCursorVisible() }
        }
        post { applyThemedCursorAndHandles() }
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