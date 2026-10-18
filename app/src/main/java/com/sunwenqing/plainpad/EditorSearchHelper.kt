package com.sunwenqing.plainpad

import android.widget.EditText

/** 查找 / 替换核心算法，与 UI 完全解耦 */
object EditorSearchHelper {

    /** @return true 表示找到并已定位选区 */
    fun findNext(editor: EditText, query: String, forward: Boolean): Boolean {
        if (query.isEmpty()) return false
        val text = editor.text.toString()
        if (text.isEmpty()) return false

        if (forward) {
            val from = editor.selectionEnd.coerceAtLeast(0)
            var index = text.indexOf(query, from, ignoreCase = true)
            if (index < 0) index = text.indexOf(query, 0, ignoreCase = true)
            if (index < 0) return false
            editor.requestFocus()
            editor.setSelection(index, index + query.length)
            return true
        } else {
            val from = (editor.selectionStart.coerceAtMost(text.length) - 1).coerceAtLeast(0)
            var index = text.lastIndexOf(query, from, ignoreCase = true)
            if (index < 0) index = text.lastIndexOf(query, text.length, ignoreCase = true)
            if (index < 0) return false
            editor.requestFocus()
            editor.setSelection(index, index + query.length)
            return true
        }
    }

    /** 当前选区若匹配则替换，然后自动跳下一个 */
    fun replaceOne(editor: EditText, query: String, replacement: String): Boolean {
        if (query.isEmpty()) return false
        val s = editor.selectionStart
        val e = editor.selectionEnd
        val editable = editor.text
        if (s in 0 until e && editable.substring(s, e).equals(query, true)) {
            editable.replace(s, e, replacement)
        }
        findNext(editor, query, true)
        return true
    }

    /** @return 替换次数；0 表示未找到 */
    fun replaceAll(editor: EditText, query: String, replacement: String): Int {
        if (query.isEmpty()) return 0
        val text = editor.text.toString()
        if (text.isEmpty()) return 0

        val sb = StringBuilder(text.length)
        var i = 0
        var count = 0
        while (i < text.length) {
            if (i + query.length <= text.length &&
                text.regionMatches(i, query, 0, query.length, ignoreCase = true)
            ) {
                sb.append(replacement)
                i += query.length
                count++
            } else {
                sb.append(text[i])
                i++
            }
        }
        if (count == 0) return 0

        val caret = editor.selectionStart.coerceAtLeast(0)
        editor.setText(sb.toString())
        editor.setSelection(caret.coerceAtMost(editor.text.length))
        return count
    }
}