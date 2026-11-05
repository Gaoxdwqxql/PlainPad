package com.sunwenqing.plainpad

import org.json.JSONArray
import org.json.JSONObject

/** 一条快速片段：名称 + 插入到光标处的文本 */
data class Snippet(val name: String, val content: String)

/**
 * 片段的存取。存在 SharedPreferences 里的一个 JSON 字符串，
 * 数据量很小（几十条），不需要数据库。
 */
object SnippetStore {

    fun load(prefs: Prefs): List<Snippet> = parse(prefs.snippetsJson)

    fun save(prefs: Prefs, list: List<Snippet>) {
        prefs.snippetsJson = serialize(list)
    }

    private fun parse(json: String): List<Snippet> {
        if (json.isBlank()) return emptyList()
        return try {
            val array = JSONArray(json)
            val result = ArrayList<Snippet>(array.length())
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val name = obj.optString("name")
                val content = obj.optString("content")
                if (name.isNotEmpty()) result.add(Snippet(name, content))
            }
            result
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun serialize(list: List<Snippet>): String {
        val array = JSONArray()
        list.forEach { snippet ->
            array.put(
                JSONObject().apply {
                    put("name", snippet.name)
                    put("content", snippet.content)
                }
            )
        }
        return array.toString()
    }
}
