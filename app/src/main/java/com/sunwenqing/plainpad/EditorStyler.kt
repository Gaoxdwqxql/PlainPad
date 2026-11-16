package com.sunwenqing.plainpad

import android.util.TypedValue
import com.sunwenqing.plainpad.databinding.ActivityMainBinding

object EditorStyler {

    private const val LINE_NUMBER_SCALE = 0.9f

    fun applyAll(binding: ActivityMainBinding, prefs: Prefs) {
        val ctx = binding.root.context

        val sizeSp = prefs.fontSize.toFloat()
        binding.editor.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)

        applyFont(binding, prefs)

        val theme = EditorThemes.byId(prefs.editorThemeId)
        EditorThemes.apply(theme, ctx, binding.editArea, binding.editor, binding.tvLineNumbers)

        binding.fastScrollBar.syncColorFromEditor()
        binding.tvLineNumbers.invalidate()
    }

    /**
     * 只重新应用字体。setText / 主题切换 / 从字体页返回后都可能把
     * TextView 的 typeface 重置回 textAppearance 的默认值，所以单独抽出来，
     * 在载入文本和每次 onResume 后都补一次。
     */
    fun applyFont(binding: ActivityMainBinding, prefs: Prefs) {
        val tf = FontHelper.getTypeface(binding.root.context, prefs)
        binding.editor.typeface = tf
        binding.tvLineNumbers.setTypefaceAndSize(
            tf,
            binding.editor.textSize * LINE_NUMBER_SCALE
        )
    }

    fun applyFontSize(binding: ActivityMainBinding, sizeSp: Float) {
        binding.editor.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        binding.tvLineNumbers.setTypefaceAndSize(
            binding.editor.typeface,
            binding.editor.textSize * LINE_NUMBER_SCALE
        )
    }
}