package com.sunwenqing.plainpad

import android.util.TypedValue
import com.sunwenqing.plainpad.databinding.ActivityMainBinding

object EditorStyler {

    private const val LINE_NUMBER_SCALE = 0.9f

    fun applyAll(binding: ActivityMainBinding, prefs: Prefs) {
        val ctx = binding.root.context

        val sizeSp = prefs.fontSize.toFloat()
        binding.editor.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)

        val tf = FontHelper.getTypeface(ctx, prefs)
        binding.editor.typeface = tf
        binding.tvLineNumbers.setTypefaceAndSize(
            tf,
            binding.editor.textSize * LINE_NUMBER_SCALE
        )

        val theme = EditorThemes.byId(prefs.editorThemeId)
        EditorThemes.apply(theme, ctx, binding.editArea, binding.editor, binding.tvLineNumbers)

        binding.fastScrollBar.syncColorFromEditor()
        binding.tvLineNumbers.invalidate()
    }

    fun applyFontSize(binding: ActivityMainBinding, sizeSp: Float) {
        binding.editor.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        binding.tvLineNumbers.setTypefaceAndSize(
            binding.editor.typeface,
            binding.editor.textSize * LINE_NUMBER_SCALE
        )
    }
}