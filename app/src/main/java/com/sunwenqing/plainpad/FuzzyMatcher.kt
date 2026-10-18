package com.sunwenqing.plainpad

/**
 * 模糊匹配工具：
 * 1. 优先匹配连续子串（大小写不敏感）
 * 2. 失败则尝试子序列匹配（关键词的字符按顺序出现在文本中，中间允许其他字符）
 *
 * 返回匹配到的字符索引列表，用于高亮；无匹配返回 null。
 */
object FuzzyMatcher {

    fun match(text: String, query: String): List<Int>? {
        if (query.isEmpty()) return null
        val lowerText = text.lowercase()
        val lowerQuery = query.lowercase()

        // 1. 连续子串匹配
        val idx = lowerText.indexOf(lowerQuery)
        if (idx >= 0) {
            return (idx until idx + query.length).toList()
        }

        // 2. 子序列匹配
        val positions = mutableListOf<Int>()
        var qi = 0
        for (i in text.indices) {
            if (qi < lowerQuery.length && lowerText[i] == lowerQuery[qi]) {
                positions.add(i)
                qi++
            }
        }
        return if (qi == lowerQuery.length) positions else null
    }
}