package com.sunwenqing.plainpad

import android.content.Context
import android.net.Uri
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

data class RecentFile(val uri: String, val name: String, val timestamp: Long)

class Prefs(context: Context) {

    private val sp = context.applicationContext
        .getSharedPreferences("plainpad_prefs", Context.MODE_PRIVATE)

    // ---------------- 编辑器基础设置 ----------------

    var fontSize: Int
        get() = sp.getInt(KEY_FONT, 16)
        set(v) = sp.edit { putInt(KEY_FONT, v) }

    var wrap: Boolean
        get() = sp.getBoolean(KEY_WRAP, true)
        set(v) = sp.edit { putBoolean(KEY_WRAP, v) }

    var showLineNumbers: Boolean
        get() = sp.getBoolean(KEY_LINE_NUMBERS, false)
        set(v) = sp.edit { putBoolean(KEY_LINE_NUMBERS, v) }

    // ---------------- 应用主题 ----------------

    var themeMode: Int
        get() = sp.getInt(KEY_THEME_MODE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        set(v) = sp.edit { putInt(KEY_THEME_MODE, v) }

    /** 主题色 id：system / purple / blue / indigo / teal / green / orange / red / pink */
    var themeColor: String
        get() = sp.getString(KEY_THEME_COLOR, "system") ?: "system"
        set(v) = sp.edit { putString(KEY_THEME_COLOR, v) }

    // ---------------- 编辑区外观 ----------------

    var editorThemeId: String
        get() = sp.getString(KEY_EDITOR_THEME, "default") ?: "default"
        set(v) = sp.edit { putString(KEY_EDITOR_THEME, v) }

    var editorFontFamily: String
        get() = sp.getString(KEY_EDITOR_FONT, "monospace") ?: "monospace"
        set(v) = sp.edit { putString(KEY_EDITOR_FONT, v) }

    // ---------------- 启动模式 ----------------

    var startupMode: Int
        get() = sp.getInt(KEY_STARTUP_MODE, 0)
        set(v) = sp.edit { putInt(KEY_STARTUP_MODE, v) }

    // ---------------- 加号行为 ----------------

    var plusReuseEmpty: Boolean
        get() = sp.getBoolean(KEY_PLUS_REUSE_EMPTY, true)
        set(v) = sp.edit { putBoolean(KEY_PLUS_REUSE_EMPTY, v) }

    // ---------------- 状态栏显示 ----------------

    var statusBarItems: String
        get() = sp.getString(KEY_STATUS_BAR_ITEMS, "chars_space,lines")
            ?: "chars_space,lines"
        set(v) = sp.edit { putString(KEY_STATUS_BAR_ITEMS, v) }

    // ---------------- 自动保存 ----------------

    var autoSaveEnabled: Boolean
        get() = sp.getBoolean(KEY_AUTO_SAVE_ENABLED, false)
        set(v) = sp.edit { putBoolean(KEY_AUTO_SAVE_ENABLED, v) }

    var autoSaveOnPause: Boolean
        get() = sp.getBoolean(KEY_AUTO_SAVE_ON_PAUSE, true)
        set(v) = sp.edit { putBoolean(KEY_AUTO_SAVE_ON_PAUSE, v) }

    var autoSaveOnTabSwitch: Boolean
        get() = sp.getBoolean(KEY_AUTO_SAVE_ON_TAB_SWITCH, true)
        set(v) = sp.edit { putBoolean(KEY_AUTO_SAVE_ON_TAB_SWITCH, v) }

    var autoSaveOnEdit: Boolean
        get() = sp.getBoolean(KEY_AUTO_SAVE_ON_EDIT, true)
        set(v) = sp.edit { putBoolean(KEY_AUTO_SAVE_ON_EDIT, v) }

    var autoSaveIntervalSec: Int
        get() = sp.getInt(KEY_AUTO_SAVE_INTERVAL, 0)
        set(v) = sp.edit { putInt(KEY_AUTO_SAVE_INTERVAL, v) }

    // ---------------- 保存时间 ----------------

    /** 上次保存时间（毫秒时间戳），0 表示从未保存过 */
    var lastSaveTime: Long
        get() = sp.getLong(KEY_LAST_SAVE_TIME, 0L)
        set(v) = sp.edit { putLong(KEY_LAST_SAVE_TIME, v) }

    // ---------------- 特殊字符显示 ----------------

    /** 是否在编辑区以符号形式显示特殊字符（不修改文本内容） */
    var showSpecialChars: Boolean
        get() = sp.getBoolean(KEY_SHOW_SPECIAL_CHARS, false)
        set(v) = sp.edit { putBoolean(KEY_SHOW_SPECIAL_CHARS, v) }

    // ---------------- 新手引导 ----------------

    var onboardingDone: Boolean
        get() = sp.getBoolean(KEY_ONBOARDING_DONE, false)
        set(v) = sp.edit { putBoolean(KEY_ONBOARDING_DONE, v) }

    // ---------------- 最近文件 ----------------

    fun getRecentFiles(): List<RecentFile> {
        val json = sp.getString(KEY_RECENT, "[]") ?: "[]"
        return try {
            val arr = JSONArray(json)
            val list = ArrayList<RecentFile>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(
                    RecentFile(
                        uri = o.getString("uri"),
                        name = o.getString("name"),
                        timestamp = o.optLong("ts", 0L)
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun addRecentFile(uri: Uri, name: String) {
        val list = getRecentFiles()
            .filterNot { it.uri == uri.toString() }
            .toMutableList()
        list.add(0, RecentFile(uri.toString(), name, System.currentTimeMillis()))
        while (list.size > MAX_RECENT) list.removeAt(list.size - 1)
        saveRecent(list)
    }

    fun clearRecentFiles() {
        saveRecent(emptyList())
    }

    private fun saveRecent(list: List<RecentFile>) {
        val arr = JSONArray()
        for (f in list) {
            val o = JSONObject()
            o.put("uri", f.uri)
            o.put("name", f.name)
            o.put("ts", f.timestamp)
            arr.put(o)
        }
        sp.edit { putString(KEY_RECENT, arr.toString()) }
    }

    // ---------------- 标签页持久化 ----------------

    fun saveTabs(docs: List<OpenDocument>) {
        val arr = JSONArray()
        for (d in docs) {
            val o = JSONObject()
            o.put("uri", d.uri?.toString() ?: "")
            o.put("name", d.displayName)
            o.put("enc", d.encoding)
            o.put("dirty", d.isDirty)
            o.put("sel", d.selStart)
            o.put("sel2", d.selEnd)
            o.put("scroll", d.scrollY)
            val needSaveText = d.uri == null || d.isDirty
            o.put("text", if (needSaveText) d.text else "")
            arr.put(o)
        }
        sp.edit { putString(KEY_SAVED_TABS, arr.toString()) }
    }

    fun loadTabs(): List<OpenDocument> {
        val json = sp.getString(KEY_SAVED_TABS, "[]") ?: "[]"
        return try {
            val arr = JSONArray(json)
            val list = ArrayList<OpenDocument>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(
                    OpenDocument(
                        uri = o.optString("uri", "").takeIf { it.isNotEmpty() }?.let(Uri::parse),
                        displayName = o.optString("name", "未命名.txt"),
                        encoding = o.optString("enc", "UTF-8"),
                        isDirty = o.optBoolean("dirty", false),
                        selStart = o.optInt("sel", 0),
                        selEnd = o.optInt("sel2", 0),
                        scrollY = o.optInt("scroll", 0),
                        text = o.optString("text", "")
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun clearSavedTabs() {
        sp.edit { remove(KEY_SAVED_TABS) }
    }

    // ---------------- keys ----------------

    private companion object {
        const val KEY_FONT = "font_size"
        const val KEY_WRAP = "wrap"
        const val KEY_LINE_NUMBERS = "show_line_numbers"
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_THEME_COLOR = "theme_color"
        const val KEY_EDITOR_THEME = "editor_theme_id"
        const val KEY_EDITOR_FONT = "editor_font_family"
        const val KEY_STARTUP_MODE = "startup_mode"
        const val KEY_PLUS_REUSE_EMPTY = "plus_reuse_empty"
        const val KEY_STATUS_BAR_ITEMS = "status_bar_items"
        const val KEY_AUTO_SAVE_ENABLED = "auto_save_enabled"
        const val KEY_AUTO_SAVE_ON_PAUSE = "auto_save_on_pause"
        const val KEY_AUTO_SAVE_ON_TAB_SWITCH = "auto_save_on_tab_switch"
        const val KEY_AUTO_SAVE_ON_EDIT = "auto_save_on_edit"
        const val KEY_AUTO_SAVE_INTERVAL = "auto_save_interval_sec"
        const val KEY_LAST_SAVE_TIME = "last_save_time"
        const val KEY_SHOW_SPECIAL_CHARS = "show_special_chars"
        const val KEY_ONBOARDING_DONE = "onboarding_done"
        const val KEY_RECENT = "recent_files"
        const val KEY_SAVED_TABS = "saved_tabs"
        const val MAX_RECENT = 10
    }
}