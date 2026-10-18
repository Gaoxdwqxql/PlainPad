package com.sunwenqing.plainpad

import android.content.Context
import android.graphics.Color
import android.util.TypedValue
import android.view.View
import android.widget.EditText
import androidx.core.content.ContextCompat

/**
 * 编辑区可选的预设主题 + 字体。
 * 每个主题是一组颜色；为 null 表示"跟随应用主题"。
 */
object EditorThemes {

    data class Theme(
        val id: String,
        val nameRes: Int,
        val bg: Int?,        // 编辑区背景
        val text: Int?,      // 正文文字
        val lineNo: Int?,    // 行号文字
        val lineNoBg: Int?   // 行号栏背景
    )

    val ALL = listOf(
        Theme("default", R.string.theme_follow_app, null, null, null, null),
        Theme("white", R.string.theme_white,
            0xFFFFFFFF.toInt(), 0xFF1A1A1A.toInt(),
            0xFF999999.toInt(), 0xFFF2F2F2.toInt()),
        Theme("sepia", R.string.theme_sepia,
            0xFFFBF0D9.toInt(), 0xFF3D3D3D.toInt(),
            0xFF9C8E71.toInt(), 0xFFF0E4C8.toInt()),
        Theme("soft_green", R.string.theme_soft_green,
            0xFFCCE8CF.toInt(), 0xFF1B3A20.toInt(),
            0xFF5A7D5E.toInt(), 0xFFB8D9BD.toInt()),
        Theme("oled_black", R.string.theme_oled_black,
            0xFF000000.toInt(), 0xFFE0E0E0.toInt(),
            0xFF666666.toInt(), 0xFF0A0A0A.toInt()),
        Theme("dark_gray", R.string.theme_dark_gray,
            0xFF1E1E1E.toInt(), 0xFFD4D4D4.toInt(),
            0xFF808080.toInt(), 0xFF252525.toInt()),
        Theme("midnight_blue", R.string.theme_midnight_blue,
            0xFF1A2030.toInt(), 0xFFD4DCE8.toInt(),
            0xFF6A7A8E.toInt(), 0xFF232A3C.toInt())
    )

    fun byId(id: String): Theme = ALL.firstOrNull { it.id == id } ?: ALL[0]

    /**
     * 把主题应用到编辑区三件套。
     * 使用 context.theme.resolveAttribute 获取默认颜色，避免 View 未 attach 时拿不到颜色。
     */
    fun apply(
        theme: Theme,
        context: Context,
        editArea: View,
        editor: EditText,
        lineNumbers: LineNumberView
    ) {
        val defaultBg = resolveAttr(context, com.google.android.material.R.attr.colorSurface, Color.WHITE)
        val defaultText = resolveAttr(context, com.google.android.material.R.attr.colorOnSurface, Color.BLACK)
        val defaultLineNoBg = resolveAttr(context, com.google.android.material.R.attr.colorSurfaceVariant, 0xFFEEEEEE.toInt())
        val defaultLineNoText = resolveAttr(context, com.google.android.material.R.attr.colorOnSurfaceVariant, 0xFF888888.toInt())

        // 编辑区背景
        editArea.setBackgroundColor(theme.bg ?: defaultBg)

        // 编辑器文字色
        editor.setTextColor(theme.text ?: defaultText)

        // 行号栏
        lineNumbers.setBackgroundColor(theme.lineNoBg ?: defaultLineNoBg)
        lineNumbers.setLineNumberTextColor(theme.lineNo ?: defaultLineNoText)
    }

    /** 从主题里解析出颜色值 */
    private fun resolveAttr(context: Context, attr: Int, fallback: Int): Int {
        return try {
            val tv = TypedValue()
            if (context.theme.resolveAttribute(attr, tv, true)) {
                if (tv.resourceId != 0) {
                    ContextCompat.getColor(context, tv.resourceId)
                } else {
                    tv.data
                }
            } else {
                fallback
            }
        } catch (_: Exception) {
            fallback
        }
    }

    // ---------------- 字体 ----------------

    /** 编辑区可选字体族 */
    val FONTS = listOf(
        "monospace" to R.string.font_monospace,
        "sans-serif" to R.string.font_sans_serif,
        "serif" to R.string.font_serif
    )

    fun fontNameRes(family: String): Int =
        FONTS.firstOrNull { it.first == family }?.second ?: R.string.font_monospace
}