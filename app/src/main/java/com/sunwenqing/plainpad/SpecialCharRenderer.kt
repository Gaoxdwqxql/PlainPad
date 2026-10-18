package com.sunwenqing.plainpad

import android.graphics.Canvas
import android.graphics.Paint
import android.text.Editable
import android.text.Spanned
import android.text.style.ReplacementSpan
import android.widget.EditText

/**
 * 在编辑器里以符号形式"显示"不可见字符。
 *
 * 原理：用 ReplacementSpan 在视觉上替换字符，**不修改文本内容**。
 * - 关闭开关后所有 Span 会被清除，文本完全恢复原状
 * - 保存/复制出去的内容永远是原始字符，不受显示影响
 *
 * 注意：普通空格（U+0020）不参与替换，只处理真正的"特殊字符"。
 */
object SpecialCharRenderer {

    /** 原字符 → 用于显示的符号 */
    private val MAP: Map<Char, String> = mapOf(
        '\t' to "\u21E5",        // Tab        → ⇥
        '\u3000' to "\u25A1",    // 全角空格    → □
        '\u00A0' to "\u25E6",    // NBSP       → ◦
        '\u200B' to "\u00B7",    // ZWSP       → ·   （改用中点，兼容性最好）
        '\u200C' to "\u00B7",    // ZWNJ       → ·
        '\u200D' to "\u00B7",    // ZWJ        → ·
        '\u2011' to "\u2011",    // 不间断连字符 → ‑
        '\uFEFF' to "\u27EA",    // BOM        → ⟪
        '\u200E' to "\u25B8",    // LRM        → ▸
        '\u200F' to "\u25C2"     // RLM        → ◂
    )

    /** 应用或清除标记 */
    fun apply(editText: EditText, enabled: Boolean) {
        val editable = editText.text ?: return
        clearSpans(editable)
        if (!enabled) return

        // 用编辑器当前文字色做 50% 透明，保证与正文对比清晰
        val color = (editText.currentTextColor and 0x00FFFFFF) or 0x88000000.toInt()

        for (i in 0 until editable.length) {
            val c = editable[i]
            val symbol = MAP[c] ?: continue
            editable.setSpan(
                SymbolSpan(symbol, color),
                i, i + 1,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
    }

    /** 清除所有本类添加的 Span */
    fun clearSpans(editable: Editable) {
        val spans = editable.getSpans(0, editable.length, SymbolSpan::class.java)
        for (s in spans) editable.removeSpan(s)
    }

    /** 把某个不可见字符替换成符号的 Span */
    private class SymbolSpan(
        private val symbol: String,
        private val color: Int
    ) : ReplacementSpan() {

        override fun getSize(
            paint: Paint,
            text: CharSequence?,
            start: Int,
            end: Int,
            fm: Paint.FontMetricsInt?
        ): Int {
            return paint.measureText(symbol).toInt()
        }

        override fun draw(
            canvas: Canvas,
            text: CharSequence?,
            start: Int,
            end: Int,
            x: Float,
            top: Int,
            y: Int,
            bottom: Int,
            paint: Paint
        ) {
            val old = paint.color
            paint.color = color
            canvas.drawText(symbol, x, y.toFloat(), paint)
            paint.color = old
        }
    }
}