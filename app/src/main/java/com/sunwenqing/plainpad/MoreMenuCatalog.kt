package com.sunwenqing.plainpad

import androidx.annotation.StringRes

/** “更多”菜单里可配置显示的项目。顺序即菜单顺序。 */
data class MoreMenuItem(
    val key: String,
    @StringRes val titleRes: Int
)

object MoreMenuCatalog {

    val ALL = listOf(
        MoreMenuItem("new", R.string.menu_new),
        MoreMenuItem("open", R.string.menu_open),
        MoreMenuItem("open_folder", R.string.menu_open_folder),
        MoreMenuItem("reopen_encoding", R.string.menu_reopen_encoding),
        MoreMenuItem("recent", R.string.menu_recent),
        MoreMenuItem("close_tab", R.string.menu_close_tab),
        MoreMenuItem("goto_line", R.string.menu_goto_line),
        MoreMenuItem("tools", R.string.menu_tools),
        MoreMenuItem("bookmarks", R.string.menu_bookmarks),
        MoreMenuItem("immersive", R.string.menu_immersive),
        MoreMenuItem("default_apps", R.string.menu_default),
        MoreMenuItem("help", R.string.menu_help),
        MoreMenuItem("about", R.string.menu_about)
    )

    val keys: Set<String> = ALL.mapTo(LinkedHashSet()) { it.key }
}
