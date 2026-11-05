package com.sunwenqing.plainpad

/**
 * 文本批量处理的纯函数集合。
 *
 * 全部以「行」为单位操作，保留原有的换行符风格（只处理 \n，\r\n 会在调用方统一）。
 * 不依赖 Android，方便单测和复用。
 */
object EditorTextTools {

    /** 按行排序，忽略大小写；空行会集中到开头，这是排序的自然结果 */
    fun sortLines(text: String, descending: Boolean): String {
        val lines = text.split('\n')
        val sorted = lines.sortedWith(String.CASE_INSENSITIVE_ORDER)
        return (if (descending) sorted.asReversed() else sorted).joinToString("\n")
    }

    /** 去除重复行，保留首次出现的位置 */
    fun dedupeLines(text: String): String {
        val seen = LinkedHashSet<String>()
        text.split('\n').forEach { seen.add(it) }
        return seen.joinToString("\n")
    }

    /** 删除所有空白行 */
    fun removeEmptyLines(text: String): String =
        text.split('\n').filter { it.isNotBlank() }.joinToString("\n")

    /** 去掉每行末尾的空格和制表符 */
    fun trimTrailingSpaces(text: String): String =
        text.split('\n').joinToString("\n") { it.trimEnd(' ', '\t') }

    /** Tab 转空格，按列对齐（制表位 width） */
    fun tabsToSpaces(text: String, width: Int = 4): String {
        if (!text.contains('\t')) return text
        val sb = StringBuilder(text.length)
        var column = 0
        for (ch in text) {
            when (ch) {
                '\n' -> {
                    sb.append(ch)
                    column = 0
                }
                '\t' -> {
                    val spaces = width - (column % width)
                    repeat(spaces) { sb.append(' ') }
                    column += spaces
                }
                else -> {
                    sb.append(ch)
                    column++
                }
            }
        }
        return sb.toString()
    }
}
