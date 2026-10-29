package com.sunwenqing.plainpad

import android.content.Context
import android.net.Uri
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class RecentFile(val uri: String, val name: String, val timestamp: Long)

class Prefs(context: Context) {

    private val appContext = context.applicationContext
    private val sp = appContext.getSharedPreferences("plainpad_prefs", Context.MODE_PRIVATE)

    fun fontFile(fileName: String): File =
        File(File(appContext.filesDir, "fonts"), fileName)

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

    var themeColor: String
        get() = sp.getString(KEY_THEME_COLOR, "system") ?: "system"
        set(v) = sp.edit { putString(KEY_THEME_COLOR, v) }

    // ---------------- 编辑区外观 ----------------

    var editorThemeId: String
        get() = sp.getString(KEY_EDITOR_THEME, "default") ?: "default"
        set(v) = sp.edit { putString(KEY_EDITOR_THEME, v) }

    /**
     * 编辑区字体 key。
     * - 内置："monospace" / "sans-serif" / "serif"
     * - 自定义复制模式："custom:<fileName>"
     * - 自定义引用模式："custom:uri:<uri>"
     * - 旧版兼容："custom"
     */
    var editorFontFamily: String
        get() = sp.getString(KEY_EDITOR_FONT, "monospace") ?: "monospace"
        set(v) = sp.edit { putString(KEY_EDITOR_FONT, v) }

    /** 旧版单字体路径，保留用于迁移 */
    var customFontPath: String
        get() = sp.getString(KEY_CUSTOM_FONT_PATH, "") ?: ""
        set(v) = sp.edit { putString(KEY_CUSTOM_FONT_PATH, v) }

    /** 已导入的自定义字体列表 */
    var customFonts: List<CustomFont>
        get() {
            val json = sp.getString(KEY_CUSTOM_FONTS, "[]") ?: "[]"
            return try {
                val arr = JSONArray(json)
                val list = ArrayList<CustomFont>(arr.length())
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val file = o.optString("file", "")
                    val uri = o.optString("uri", "")
                    if (file.isEmpty() && uri.isEmpty()) continue
                    val name = o.optString("name", file.ifEmpty { uri }).ifEmpty { "font" }
                    list.add(CustomFont(name, file, uri))
                }
                list
            } catch (_: Exception) {
                emptyList()
            }
        }
        set(v) {
            val arr = JSONArray()
            for (f in v) {
                arr.put(
                    JSONObject()
                        .put("name", f.displayName)
                        .put("file", f.fileName)
                        .put("uri", f.uri)
                )
            }
            sp.edit { putString(KEY_CUSTOM_FONTS, arr.toString()) }
        }

    /** 字体是否拷贝到私有目录再加载。默认 true。 */
    var copyFontToPrivate: Boolean
        get() = sp.getBoolean(KEY_COPY_FONT, true)
        set(v) = sp.edit { putBoolean(KEY_COPY_FONT, v) }

    // ---------------- 启动模式 ----------------

    var startupMode: Int
        get() = sp.getInt(KEY_STARTUP_MODE, 0)
        set(v) = sp.edit { putInt(KEY_STARTUP_MODE, v) }

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

    var lastSaveTime: Long
        get() = sp.getLong(KEY_LAST_SAVE_TIME, 0L)
        set(v) = sp.edit { putLong(KEY_LAST_SAVE_TIME, v) }

    var showSpecialChars: Boolean
        get() = sp.getBoolean(KEY_SHOW_SPECIAL_CHARS, false)
        set(v) = sp.edit { putBoolean(KEY_SHOW_SPECIAL_CHARS, v) }

    /** 保存 UTF-8 文件时是否写入 BOM。关闭时不主动添加，但原文件已有的 BOM 会保留。 */
    var utf8Bom: Boolean
        get() = sp.getBoolean(KEY_UTF8_BOM, false)
        set(v) = sp.edit { putBoolean(KEY_UTF8_BOM, v) }

    var onboardingDone: Boolean
        get() = sp.getBoolean(KEY_ONBOARDING_DONE, false)
        set(v) = sp.edit { putBoolean(KEY_ONBOARDING_DONE, v) }

    // ---------------- 开发者选项入口 ----------------

    var devOptionsUnlocked: Boolean
        get() = sp.getBoolean(KEY_DEV_OPTIONS_UNLOCKED, false)
        set(v) = sp.edit { putBoolean(KEY_DEV_OPTIONS_UNLOCKED, v) }

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

    fun saveTabs(docs: List<OpenDocument>, sync: Boolean = false) {
        val arr = JSONArray()
        for (d in docs) {
            val o = JSONObject()
            o.put("uri", d.uri?.toString() ?: "")
            o.put("name", d.displayName)
            o.put("enc", d.encoding)
            o.put("dirty", d.isDirty)
            o.put("readonly", d.isReadOnly)
            o.put("sel", d.selStart)
            o.put("sel2", d.selEnd)
            o.put("scroll", d.scrollY)
            o.put("bom", d.hasBom)
            o.put("draftId", d.draftId)
            val needSaveText = d.uri == null || d.isDirty
            o.put("text", if (needSaveText) d.text else "")
            arr.put(o)
        }
        if (sync) {
            sp.edit().putString(KEY_SAVED_TABS, arr.toString()).commit()
        } else {
            sp.edit { putString(KEY_SAVED_TABS, arr.toString()) }
        }
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
                        isReadOnly = o.optBoolean("readonly", false),
                        selStart = o.optInt("sel", 0),
                        selEnd = o.optInt("sel2", 0),
                        scrollY = o.optInt("scroll", 0),
                        hasBom = o.optBoolean("bom", false),
                        text = o.optString("text", ""),
                        draftId = o.optString("draftId", "")
                            .takeIf { it.isNotEmpty() } ?: UUID.randomUUID().toString()
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

    private companion object {
        const val KEY_FONT = "font_size"
        const val KEY_WRAP = "wrap"
        const val KEY_LINE_NUMBERS = "show_line_numbers"
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_THEME_COLOR = "theme_color"
        const val KEY_EDITOR_THEME = "editor_theme_id"
        const val KEY_EDITOR_FONT = "editor_font_family"
        const val KEY_CUSTOM_FONT_PATH = "custom_font_path"
        const val KEY_CUSTOM_FONTS = "custom_fonts"
        const val KEY_COPY_FONT = "copy_font_to_private"
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
        const val KEY_UTF8_BOM = "utf8_bom"
        const val KEY_ONBOARDING_DONE = "onboarding_done"
        const val KEY_DEV_OPTIONS_UNLOCKED = "dev_options_unlocked"
        const val KEY_RECENT = "recent_files"
        const val KEY_SAVED_TABS = "saved_tabs"
        const val MAX_RECENT = 10
    }
}