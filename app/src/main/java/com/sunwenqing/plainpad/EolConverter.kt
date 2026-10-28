package com.sunwenqing.plainpad

import android.widget.TextView

/**
 * EOL 检测与转换。
 * - labelOf：返回 "LF" / "CRLF" / "Mixed"，供 UI 显示。
 * - convert：返回转换后的文本；null 表示无需转换。
 */
object EolConverter {

    fun labelOf(text: CharSequence?): String =
        if (text == null) "LF" else EditorCommands.detectEol(text)

    fun convert(text: String, targetEol: String): String? {
        val normalized = text.replace("\r\n", "\n")
        val newText = if (targetEol == "\r\n") normalized.replace("\n", "\r\n") else normalized
        return if (newText == text) null else newText
    }

    fun applyLabel(tv: TextView, text: CharSequence?): String {
        val label = labelOf(text)
        tv.text = label
        return label
    }
}