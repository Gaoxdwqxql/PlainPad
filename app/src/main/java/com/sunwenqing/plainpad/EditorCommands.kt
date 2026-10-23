package com.sunwenqing.plainpad

import android.widget.EditText

/**
 * 编辑器命令与统计工具。
 */
object EditorCommands {

    // ---------------- 撤销 / 重做 ----------------

    fun undo(undoManager: UndoManager): Snapshot? = undoManager.undo()
    fun redo(undoManager: UndoManager): Snapshot? = undoManager.redo()

    // ---------------- 行号相关 ----------------

    fun countLogicalLines(text: CharSequence): Int {
        if (text.isEmpty()) return 1
        var lines = 1
        for (c in text) if (c == '\n') lines++
        return lines
    }

    fun offsetOfLine(text: CharSequence, lineNo: Int): Int {
        if (lineNo < 1) return -1
        if (lineNo == 1) return 0
        var currentLine = 1
        for (i in text.indices) {
            if (text[i] == '\n') {
                currentLine++
                if (currentLine == lineNo) return i + 1
            }
        }
        return -1
    }

    fun scrollToOffset(editor: EditText, offset: Int) {
        editor.post {
            val layout = editor.layout ?: return@post
            val y = layout.getLineTop(layout.getLineForOffset(offset))
            val visibleBottom = editor.scrollY + editor.height
            if (y < editor.scrollY || y > visibleBottom) {
                editor.scrollTo(0, (y - editor.height / 3).coerceAtLeast(0))
            }
        }
    }

    // ---------------- 统计 ----------------

    data class EditorStats(
        val charsWithSpace: Int,
        val charsNoSpace: Int,
        val visibleChars: Int,
        val invisibleChars: Int,
        val letters: Int,
        val digits: Int,
        val words: Int,
        val totalLines: Int,
        val nonEmptyLines: Int,
        val paragraphs: Int,
        val chineseChars: Int,
        val maxLineWidth: Int
    )

    /**
     * 统计文本各项指标。
     * 注意：
     * - charsWithSpace 不含 \n 和 \r（修复"把换行算进字符数"的 bug）
     * - charsNoSpace 仅统计非空白字符
     * - invisibleChars = charsWithSpace - charsNoSpace
     */
    fun computeStats(text: CharSequence): EditorStats {
        var charsWithSpace = 0      // 含空格，但排除 \n 和 \r
        var charsNoSpace = 0        // 非空白字符
        var letters = 0
        var digits = 0
        var chinese = 0
        var totalLines = 1
        var nonEmptyLines = 0
        var paragraphs = 0
        var words = 0
        var inWord = false
        var lineHasContent = false
        var inParagraph = false

        // 每行可见宽度（半角宽）统计
        var currentLineWidth = 0
        var maxLineWidth = 0

        for (i in text.indices) {
            val c = text[i]

            if (c == '\n') {
                if (inWord) { words++; inWord = false }
                if (lineHasContent) nonEmptyLines++
                inParagraph = false
                lineHasContent = false
                if (currentLineWidth > maxLineWidth) maxLineWidth = currentLineWidth
                currentLineWidth = 0
                totalLines++
                continue
            }
            if (c == '\r') {
                // 兼容 CRLF 与老 Mac 风格换行：不计入字符数，也不单独作为换行
                continue
            }

            // 非换行字符，计入"含空格"总数
            charsWithSpace++

            if (c.isWhitespace()) {
                if (inWord) { words++; inWord = false }
                currentLineWidth += 1 // 空格按 1 宽计算
            } else {
                charsNoSpace++
                lineHasContent = true
                if (!inWord) { words++; inWord = true }
                if (!inParagraph) { paragraphs++; inParagraph = true }

                val code = c.code
                if ((code in 0x41..0x5A) || (code in 0x61..0x7A)) letters++
                if (code in 0x30..0x39) digits++
                if (isChineseChar(c)) chinese++

                currentLineWidth += charHalfWidth(c)
            }
        }
        if (inWord) words++
        if (lineHasContent) nonEmptyLines++
        if (currentLineWidth > maxLineWidth) maxLineWidth = currentLineWidth

        val invisible = charsWithSpace - charsNoSpace

        return EditorStats(
            charsWithSpace = charsWithSpace,
            charsNoSpace = charsNoSpace,
            visibleChars = charsNoSpace,
            invisibleChars = invisible,
            letters = letters,
            digits = digits,
            words = words,
            totalLines = totalLines,
            nonEmptyLines = nonEmptyLines,
            paragraphs = paragraphs,
            chineseChars = chinese,
            maxLineWidth = maxLineWidth
        )
    }

    private fun isChineseChar(c: Char): Boolean {
        val code = c.code
        return code in 0x4E00..0x9FFF ||
                code in 0x3400..0x4DBF ||
                code in 0xF900..0xFAFF
    }

    /** ASCII 及半角字符算 1 宽，其他（中文、全角）算 2 宽 */
    private fun charHalfWidth(c: Char): Int = if (c.code < 0x80) 1 else 2

    // ---------------- 换行符检测 ----------------

    /**
     * 检测文本的换行符类型。
     * 为了性能，最多只扫描前 100_000 个字符，足以应对所有场景。
     */
    fun detectEol(text: CharSequence): String {
        var crlfCount = 0
        var lfCount = 0
        var idx = 0
        val limit = minOf(text.length, 100_000)

        while (idx < limit) {
            val lfIdx = text.indexOf('\n', idx)
            if (lfIdx == -1 || lfIdx >= limit) break
            if (lfIdx > 0 && text[lfIdx - 1] == '\r') {
                crlfCount++
            } else {
                lfCount++
            }
            idx = lfIdx + 1
        }

        return when {
            crlfCount > 0 && lfCount > 0 -> "Mixed"
            crlfCount > 0 -> "CRLF"
            else -> "LF" // 纯 LF，或者文本没有任何换行符，默认按 LF 处理
        }
    }
}