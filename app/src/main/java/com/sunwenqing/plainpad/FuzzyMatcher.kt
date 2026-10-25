package com.sunwenqing.plainpad

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
                // ★ 过滤控制字符：如果匹配到的是换行符或制表符，直接跳过
                if (lowerText[i] == '\n' || lowerText[i] == '\r' || lowerText[i] == '\t') {
                    continue
                }
                positions.add(i)
                qi++
            }
        }
        return if (qi == lowerQuery.length) positions else null
    }
}