package com.sunwenqing.plainpad

import androidx.appcompat.app.AppCompatDelegate
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * PlainPad 配置的 INI 导入/导出。
 *
 * 不导出涉及文件路径和列表的内容：
 * - customFonts / customFontPath（字体文件路径，跨设备无意义）
 * - savedTabs / recentFiles（用户会话数据）
 * - lastSaveTime / onboardingDone（运行时状态）
 * - 语言（由 AppCompatDelegate 管理，不在 Prefs 里）
 */
object PrefsIni {

    private const val SECTION_EDITOR = "editor"
    private const val SECTION_APPEARANCE = "appearance"
    private const val SECTION_SAVE = "save"
    private const val SECTION_STATUS = "status"
    private const val SECTION_TABS = "tabs"

    private val VALID_THEME_COLORS = setOf(
        "system", "purple", "blue", "indigo", "teal",
        "green", "orange", "red", "pink"
    )

    // ---------------- 导出 ----------------

    fun export(prefs: Prefs): String {
        val sb = StringBuilder()
        val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())

        sb.append("; PlainPad configuration\n")
        sb.append("; Generated: ").append(now).append('\n')
        sb.append("; Format version: 1\n\n")

        sb.append('[').append(SECTION_EDITOR).append("]\n")
        sb.append("fontSize=").append(prefs.fontSize).append('\n')
        sb.append("wrap=").append(prefs.wrap).append('\n')
        sb.append("showLineNumbers=").append(prefs.showLineNumbers).append('\n')
        sb.append("showSpecialChars=").append(prefs.showSpecialChars).append('\n')
        sb.append("currentLineHighlight=").append(prefs.currentLineHighlight).append('\n')
        sb.append("editorThemeId=").append(prefs.editorThemeId).append('\n')
        sb.append("editorFontFamily=").append(prefs.editorFontFamily).append('\n')
        sb.append('\n')

        sb.append('[').append(SECTION_APPEARANCE).append("]\n")
        sb.append("themeMode=").append(prefs.themeMode).append('\n')
        sb.append("themeColor=").append(prefs.themeColor).append('\n')
        sb.append("copyFontToPrivate=").append(prefs.copyFontToPrivate).append('\n')
        sb.append('\n')

        sb.append('[').append(SECTION_SAVE).append("]\n")
        sb.append("autoSaveEnabled=").append(prefs.autoSaveEnabled).append('\n')
        sb.append("autoSaveOnPause=").append(prefs.autoSaveOnPause).append('\n')
        sb.append("autoSaveOnTabSwitch=").append(prefs.autoSaveOnTabSwitch).append('\n')
        sb.append("autoSaveOnEdit=").append(prefs.autoSaveOnEdit).append('\n')
        sb.append("autoSaveIntervalSec=").append(prefs.autoSaveIntervalSec).append('\n')
        sb.append("utf8Bom=").append(prefs.utf8Bom).append('\n')
        sb.append('\n')

        sb.append('[').append(SECTION_STATUS).append("]\n")
        sb.append("statusBarItems=").append(prefs.statusBarItems).append('\n')
        sb.append('\n')

        sb.append('[').append(SECTION_TABS).append("]\n")
        sb.append("startupMode=").append(prefs.startupMode).append('\n')
        sb.append("plusReuseEmpty=").append(prefs.plusReuseEmpty).append('\n')

        return sb.toString()
    }

    // ---------------- 导入 ----------------

    fun import(prefs: Prefs, content: String): Result<Int> {
        return try {
            val sections = parse(content)
            var count = 0

            sections[SECTION_EDITOR]?.let { s ->
                s["fontSize"]?.toIntOrNull()?.takeIf { it in 10..40 }?.let { prefs.fontSize = it; count++ }
                s["wrap"]?.toBooleanStrictOrNull()?.let { prefs.wrap = it; count++ }
                s["showLineNumbers"]?.toBooleanStrictOrNull()?.let { prefs.showLineNumbers = it; count++ }
                s["showSpecialChars"]?.toBooleanStrictOrNull()?.let { prefs.showSpecialChars = it; count++ }
                s["currentLineHighlight"]?.toBooleanStrictOrNull()?.let { prefs.currentLineHighlight = it; count++ }
                s["editorThemeId"]?.takeIf { it.isNotBlank() }?.let { prefs.editorThemeId = it; count++ }
                s["editorFontFamily"]?.takeIf { it.isNotBlank() }?.let { prefs.editorFontFamily = it; count++ }
            }

            sections[SECTION_APPEARANCE]?.let { s ->
                s["themeMode"]?.toIntOrNull()?.takeIf { isValidThemeMode(it) }?.let {
                    prefs.themeMode = it
                    count++
                }
                s["themeColor"]?.takeIf { it in VALID_THEME_COLORS }?.let {
                    prefs.themeColor = it
                    count++
                }
                s["copyFontToPrivate"]?.toBooleanStrictOrNull()?.let { prefs.copyFontToPrivate = it; count++ }
            }

            sections[SECTION_SAVE]?.let { s ->
                s["autoSaveEnabled"]?.toBooleanStrictOrNull()?.let { prefs.autoSaveEnabled = it; count++ }
                s["autoSaveOnPause"]?.toBooleanStrictOrNull()?.let { prefs.autoSaveOnPause = it; count++ }
                s["autoSaveOnTabSwitch"]?.toBooleanStrictOrNull()?.let { prefs.autoSaveOnTabSwitch = it; count++ }
                s["autoSaveOnEdit"]?.toBooleanStrictOrNull()?.let { prefs.autoSaveOnEdit = it; count++ }
                s["autoSaveIntervalSec"]?.toIntOrNull()?.takeIf { it >= 0 }?.let {
                    prefs.autoSaveIntervalSec = it
                    count++
                }
                s["utf8Bom"]?.toBooleanStrictOrNull()?.let { prefs.utf8Bom = it; count++ }
            }

            sections[SECTION_STATUS]?.let { s ->
                s["statusBarItems"]?.takeIf { it.isNotBlank() }?.let {
                    prefs.statusBarItems = it
                    count++
                }
            }

            sections[SECTION_TABS]?.let { s ->
                s["startupMode"]?.toIntOrNull()?.takeIf { it in 0..2 }?.let { prefs.startupMode = it; count++ }
                s["plusReuseEmpty"]?.toBooleanStrictOrNull()?.let { prefs.plusReuseEmpty = it; count++ }
            }

            if (count == 0) Result.failure(Exception("No valid configuration found"))
            else Result.success(count)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ---------------- 解析 ----------------

    private fun parse(content: String): Map<String, Map<String, String>> {
        val result = mutableMapOf<String, MutableMap<String, String>>()
        var currentSection: String? = null

        content.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty()) return@forEach
            if (line.startsWith(";") || line.startsWith("#")) return@forEach

            if (line.startsWith("[") && line.endsWith("]")) {
                currentSection = line.substring(1, line.length - 1).trim()
                result.getOrPut(currentSection!!) { mutableMapOf() }
            } else if (currentSection != null) {
                val idx = line.indexOf('=')
                if (idx > 0) {
                    val key = line.substring(0, idx).trim()
                    val value = line.substring(idx + 1).trim()
                    if (key.isNotEmpty()) {
                        result[currentSection!!]!![key] = value
                    }
                }
            }
        }
        return result
    }

    private fun isValidThemeMode(mode: Int): Boolean =
        mode == AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM ||
                mode == AppCompatDelegate.MODE_NIGHT_NO ||
                mode == AppCompatDelegate.MODE_NIGHT_YES
}