// PlainEditText.kt
package com.sunwenqing.plainpad

import android.content.Context
import android.os.Build
import android.util.AttributeSet
import android.view.textclassifier.TextClassifier
import android.widget.EditText
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.appcompat.widget.AppCompatEditText

/**
 * 可控 bringPointIntoView 的 EditText。
 *
 * 另外：
 * - 禁用 Android 9+ 的系统文本放大镜（水滴形光标放大镜），
 *   避免出现"光标下方套一层半透明圆"的视觉干扰。
 * - 禁用系统 TextClassifier。
 *   长按选中时系统会把文本通过 Binder 传给 TextClassificationManager，
 *   大文件会触发 TransactionTooLargeException，且调用在主线程，直接卡死。
 */
class PlainEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.appcompat.R.attr.editTextStyle
) : AppCompatEditText(context, attrs, defStyleAttr) {

    @Volatile
    private var allowBring = false

    init {
        disableMagnifier()
        disableTextClassifier()
    }

    /**
     * 禁用系统文本放大镜。
     * - Android 13 (API 33) 起是 public API：setMagnifierEnabled(boolean)
     * - Android 9~12 (API 28~32) 是 hidden API，需要反射
     * - 反射失败不影响其他功能
     */
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
            // 该 ROM 没有这个方法或字段名不同，忽略
        }
    }

    /**
     * 禁用系统 TextClassifier。
     * 放到单独的 @RequiresApi(26) 对象里，避免 API 24/25 加载本类时解析
     * android.view.textclassifier.TextClassifier。
     */
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

    /** 手动检查光标是否在视口内；不在则滚到可见 */
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

    /** 一次性让光标滚入视口（用于"跳转到行"等场景） */
    fun forceBringCursorIntoView() {
        allowBring = true
        try {
            bringPointIntoView(selectionEnd)
        } finally {
            allowBring = false
        }
    }
}