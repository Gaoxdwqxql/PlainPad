package com.sunwenqing.plainpad

import android.text.Editable
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.widget.EditText
import java.util.regex.Pattern

/**
 * 查找时高亮全部匹配项。
 *
 * 只往 Editable 上加/删 [BackgroundColorSpan]，不改动文本内容，
 * 关闭查找弹窗后所有高亮会被清除。
 */
object SearchHighlighter {

    /** 最多高亮多少处。超长文件里 span 过多会让排版明显变慢 */
    private const val MAX_MATCHES = 500

    private val spans = ArrayList<BackgroundColorSpan>()

    /**
     * 按当前条件重新高亮。返回实际高亮的数量。
     * 查找词为空、正则非法时只清除旧高亮。
     */
    fun apply(editor: EditText, query: String, options: SearchOptions, color: Int): Int {
        val editable = editor.text ?: return 0
        clear(editor)
        if (query.isEmpty()) return 0

        val text = editable.toString()
        if (text.isEmpty()) return 0

        var count = 0
        if (options.regex) {
            val pattern = try {
                Pattern.compile(
                    query,
                    if (options.caseSensitive) 0
                    else Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE
                )
            } catch (_: Exception) {
                return 0
            }
            val matcher = pattern.matcher(text)
            while (count < MAX_MATCHES && matcher.find()) {
                // 跳过零长度匹配，否则会把整篇文字刷上颜色
                if (matcher.end() > matcher.start()) {
                    addSpan(editable, matcher.start(), matcher.end(), color)
                    count++
                }
            }
        } else {
            var index = text.indexOf(query, 0, ignoreCase = !options.caseSensitive)
            while (index >= 0 && count < MAX_MATCHES) {
                addSpan(editable, index, index + query.length, color)
                count++
                index = text.indexOf(query, index + query.length, ignoreCase = !options.caseSensitive)
            }
        }
        return count
    }

    fun clear(editor: EditText) {
        val editable = editor.text ?: return
        spans.forEach { editable.removeSpan(it) }
        spans.clear()
    }

    private fun addSpan(editable: Editable, start: Int, end: Int, color: Int) {
        if (start < 0 || end > editable.length || end <= start) return
        val span = BackgroundColorSpan(color)
        editable.setSpan(span, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        spans.add(span)
    }
}
