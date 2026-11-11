package com.sunwenqing.plainpad

import android.content.Context
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip

/**
 * 墨水屏模式下的视图树整理：
 * - 卡片、按钮、标签统一改成直角，去掉阴影，卡片改用 1dp 黑描边保证层次
 * - Toolbar 强制白底黑字、黑色图标
 *
 * 颜色本身由 [R.style.ThemeOverlay_PlainPad_Eink] 覆盖，这里只处理布局里写死的
 * 圆角、elevation 这类主题管不到的属性。
 */
object EinkStyler {

    /** 墨水屏下编辑区使用的纯黑白主题 */
    val EDITOR_THEME = EditorThemes.Theme(
        id = "eink",
        nameRes = 0,
        bg = Color.WHITE,
        text = Color.BLACK,
        lineNo = Color.BLACK,
        lineNoBg = Color.WHITE
    )

    fun isEnabled(context: Context): Boolean =
        Prefs(context.applicationContext).einkMode

    fun apply(root: View?) {
        if (root == null) return
        if (!isEnabled(root.context)) return
        walk(root)
    }

    private fun walk(view: View) {
        // 去掉所有 elevation，避免残留投影
        view.elevation = 0f
        view.translationZ = 0f

        when (view) {
            is MaterialCardView -> {
                view.radius = 0f
                view.cardElevation = 0f
                view.strokeWidth = dp(view, 1f)
                view.strokeColor = Color.BLACK
                view.setCardBackgroundColor(Color.WHITE)
            }

            is MaterialToolbar -> {
                view.setBackgroundColor(Color.WHITE)
                view.setTitleTextColor(Color.BLACK)
                view.setSubtitleTextColor(Color.BLACK)
                view.navigationIcon = view.navigationIcon?.mutate()?.apply {
                    setTint(Color.BLACK)
                }
                view.overflowIcon = view.overflowIcon?.mutate()?.apply {
                    setTint(Color.BLACK)
                }
            }

            is Chip -> {
                view.chipCornerRadius = 0f
            }

            is MaterialButton -> {
                view.cornerRadius = 0
            }
        }

        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                walk(view.getChildAt(i))
            }
        }
    }

    private fun dp(view: View, value: Float): Int =
        (value * view.resources.displayMetrics.density).toInt()
}
