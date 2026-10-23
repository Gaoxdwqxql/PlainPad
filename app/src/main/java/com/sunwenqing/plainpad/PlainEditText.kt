package com.sunwenqing.plainpad

import android.content.Context
import android.os.Build
import android.util.AttributeSet
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

    // ★ 新增：只读模式。设为 true 时表现类似 TextView，支持滚动/选中/复制，但禁止修改
    var isReadOnlyMode = false
        set(value) {
            field = value
            isCursorVisible = !value
        }

    init {
        disableMagnifier()
        disableTextClassifier()
    }

    // ★ 重写此方法。返回 false 时，系统底层会拒绝按键输入、拦截软键盘输入和粘贴操作
    override fun getDefaultEditable(): Boolean {
        return !isReadOnlyMode
    }

    private fun disableMagnifier() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        try {
            val method = TextView::class.java
                .getDeclaredMethod(
                    "setMagnifierEnabled",
                    Boolean::class.javaPrimitiveType
                )
            method.isAccessible = true
            method.invoke(this, false)
        } catch (_: Exception) {
        }
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