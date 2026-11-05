package com.sunwenqing.plainpad

import androidx.annotation.StringRes

/**
 * 帮助页的条目清单。
 *
 * 帮助页原本是 13 张手写卡片——加折叠和搜索后，每张卡片都要有 header / body / 箭头三套 id，
 * 手写 XML 会非常啰嗦且容易漏改。这里改成数据驱动：页面按这份清单生成卡片，
 * 折叠状态按 [id] 记录，搜索时匹配标题与正文。
 */
data class HelpSection(
    val id: String,
    @StringRes val titleRes: Int,
    @StringRes val contentRes: Int
)

object HelpCatalog {

    val ALL = listOf(
        HelpSection("file", R.string.help_section_file, R.string.help_file_content),
        HelpSection("edit", R.string.help_section_edit, R.string.help_edit_content),
        HelpSection("stats", R.string.help_section_stats, R.string.help_stats_content),
        HelpSection("tabs", R.string.help_section_tabs, R.string.help_tabs_content),
        HelpSection("appearance", R.string.help_section_appearance, R.string.help_appearance_content),
        HelpSection("gesture", R.string.help_section_gesture, R.string.help_gesture_content),
        HelpSection("autosave", R.string.help_section_autosave, R.string.help_autosave_content),
        HelpSection("encoding", R.string.help_section_encoding, R.string.help_encoding_content),
        HelpSection("eol", R.string.help_section_eol, R.string.help_eol_content),
        HelpSection("large_file", R.string.help_section_large_file, R.string.help_large_file_content),
        HelpSection("immersive", R.string.help_section_immersive, R.string.help_immersive_content),
        HelpSection("bookmarks", R.string.help_section_bookmarks, R.string.help_bookmarks_content),
        HelpSection("bom", R.string.help_section_bom, R.string.help_bom_content),
        HelpSection("crash", R.string.help_section_crash, R.string.help_crash_content),
        HelpSection("about", R.string.help_section_about, R.string.help_about_content)
    )
}
