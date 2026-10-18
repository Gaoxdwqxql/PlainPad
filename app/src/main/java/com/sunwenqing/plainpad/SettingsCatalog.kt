package com.sunwenqing.plainpad

import androidx.annotation.StringRes

/**
 * 设置项目录。
 * key 与 activity_settings.xml 里 android:tag="search:key" 中的 key 一致。
 */
data class SettingsItem(
    val key: String,
    @StringRes val titleRes: Int,
    @StringRes val descRes: Int,   // 0 表示无描述
    val section: String            // editing / save / appearance / tabs
)

object SettingsCatalog {

    val ALL = listOf(
        // 编辑体验
        SettingsItem("font_size", R.string.settings_font_size_title, R.string.font_size_label, "editing"),
        SettingsItem("wrap", R.string.menu_wrap, R.string.settings_wrap_desc, "editing"),
        SettingsItem("line_numbers", R.string.menu_show_line_numbers, 0, "editing"),
        SettingsItem("show_special_chars", R.string.settings_show_special_chars, R.string.settings_show_special_chars_hint, "editing"),
        SettingsItem("replace_rules", R.string.special_chars_replace_rules_title, R.string.settings_replace_rules_desc, "editing"),
        SettingsItem("status_bar", R.string.settings_status_bar, R.string.status_bar_hint, "editing"),

        // 保存
        SettingsItem("auto_save", R.string.settings_auto_save, R.string.auto_save_hint, "save"),
        SettingsItem("auto_save_on_pause", R.string.auto_save_on_pause, 0, "save"),
        SettingsItem("auto_save_on_tab_switch", R.string.auto_save_on_tab_switch, 0, "save"),
        SettingsItem("auto_save_on_edit", R.string.auto_save_on_edit, R.string.auto_save_on_edit_hint, "save"),
        SettingsItem("auto_save_interval", R.string.auto_save_interval, 0, "save"),

        // 外观
        SettingsItem("editor_theme", R.string.settings_editor_theme, R.string.settings_editor_theme_desc, "appearance"),
        SettingsItem("editor_font", R.string.settings_editor_font, R.string.settings_editor_font_desc, "appearance"),
        SettingsItem("app_theme", R.string.settings_theme, R.string.settings_theme_desc, "appearance"),

        // 标签页
        SettingsItem("plus_reuse", R.string.settings_plus_behavior, R.string.plus_reuse_empty, "tabs"),
        SettingsItem("startup_mode", R.string.settings_startup_mode, R.string.settings_startup_hint, "tabs")
    )

    fun sectionNameRes(section: String): Int = when (section) {
        "editing" -> R.string.section_editing
        "save" -> R.string.section_save
        "appearance" -> R.string.section_appearance
        "tabs" -> R.string.section_tabs
        else -> R.string.settings_title
    }
}