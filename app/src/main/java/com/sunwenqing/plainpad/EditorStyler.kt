package com.sunwenqing.plainpad

import android.graphics.Typeface
import android.util.TypedValue
import com.sunwenqing.plainpad.databinding.ActivityMainBinding

/**
 * 编辑器外观应用工具。
 * 负责把 Prefs 中的"字号 / 字体族 / 编辑区主题"三项设置一次性应用到 UI 上。
 */
object EditorStyler {

    /** 一次性应用全部外观设置 */
    fun applyAll(binding: ActivityMainBinding, prefs: Prefs) {
        val ctx = binding.root.context

        val sizeSp = prefs.fontSize.toFloat()
        binding.editor.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)

        val tf = Typeface.create(prefs.editorFontFamily, Typeface.NORMAL)
        binding.editor.typeface = tf
        binding.tvLineNumbers.setTypefaceAndSize(tf, binding.editor.textSize)

        val theme = EditorThemes.byId(prefs.editorThemeId)
        EditorThemes.apply(
            theme,
            ctx,
            binding.editArea,
            binding.editor,
            binding.tvLineNumbers
        )

        // 滚动条颜色跟随编辑器文字色
        binding.fastScrollBar.syncColorFromEditor()

        binding.tvLineNumbers.invalidate()
    }

    /** 仅应用字号（用于双指缩放实时预览） */
    fun applyFontSize(binding: ActivityMainBinding, sizeSp: Float) {
        binding.editor.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        binding.tvLineNumbers.setTypefaceAndSize(
            binding.editor.typeface,
            binding.editor.textSize
        )
    }
}