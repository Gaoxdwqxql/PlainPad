package com.sunwenqing.plainpad

import android.content.Context
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
import android.widget.EditText
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.appcompat.widget.AppCompatEditText

class PlainEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.appcompat.R.attr.editTextStyle
) : AppCompatEditText(context, attrs, defStyleAttr) {

    @Volatile
    private var allowBring = false

    private val MAX_IME_TEXT_LENGTH = 100_000

    var isReadOnlyMode = false
        set(value) {
            field = value
            isCursorVisible = !value
            showSoftInputOnFocus = !value
        }

    // ★ 新增：完全禁用编辑器交互（针对大文件模式）
    var isInteractionDisabled = false
        set(value) {
            field = value
            if (value) {
                isFocusable = false
                isFocusableInTouchMode = false
                isLongClickable = false
                setTextIsSelectable(false)
                // 隐藏光标
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
        disableTextClassifier()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            breakStrategy = Layout.BREAK_STRATEGY_SIMPLE
            hyphenationFrequency = Layout.HYPHENATION_FREQUENCY_NONE
        }
    }

    // ★ 拦截触摸事件：如果交互被禁用，直接 return true 吞掉事件，防止底层去算长按和分词
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (isInteractionDisabled) {
            // 只允许滑动和滚动（通过父容器或自身的滚动机制），不允许点击或长按
            return true
        }
        return super.onTouchEvent(event)
    }

    override fun getDefaultEditable(): Boolean {
        return !isReadOnlyMode
    }

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        val superConnection = super.onCreateInputConnection(outAttrs) ?: return null
        return object : InputConnectionWrapper(superConnection, false) {
            override fun getExtractedText(request: ExtractedTextRequest?, flags: Int): ExtractedText? {
                if ((text?.length ?: 0) > MAX_IME_TEXT_LENGTH) return null
                return super.getExtractedText(request, flags)
            }
            override fun getTextBeforeCursor(n: Int, flags: Int): CharSequence? {
                return super.getTextBeforeCursor(n.coerceAtMost(1000), flags)
            }
            override fun getTextAfterCursor(n: Int, flags: Int): CharSequence? {
                return super.getTextAfterCursor(n.coerceAtMost(1000), flags)
            }
        }
    }

    private fun disableMagnifier() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        try {
            val method = TextView::class.java.getDeclaredMethod("setMagnifierEnabled", Boolean::class.javaPrimitiveType)
            method.isAccessible = true
            method.invoke(this, false)
        } catch (_: Exception) {}
    }

    private fun disableTextClassifier() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            TextClassifierDisabler.disable(this)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private object TextClassifierDisabler {
        fun disable(editText: EditText) {
            editText.setTextClassifier(TextClassifier.NO_OP)
        }
    }

    override fun bringPointIntoView(offset: Int): Boolean {
        return if (allowBring) super.bringPointIntoView(offset) else false
    }

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        if (!allowBring) {
            post { ensureCursorVisible() }
        }
    }

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