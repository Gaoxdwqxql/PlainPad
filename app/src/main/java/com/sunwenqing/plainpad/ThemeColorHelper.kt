package com.sunwenqing.plainpad

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.util.TypedValue
import androidx.annotation.StyleRes
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.material.color.DynamicColors

object ThemeColorHelper {

    @StyleRes
    private fun overlayFor(colorId: String): Int = when (colorId) {
        "purple" -> R.style.ThemeOverlay_PlainPad_Color_Purple
        "blue" -> R.style.ThemeOverlay_PlainPad_Color_Blue
        "indigo" -> R.style.ThemeOverlay_PlainPad_Color_Indigo
        "teal" -> R.style.ThemeOverlay_PlainPad_Color_Teal
        "green" -> R.style.ThemeOverlay_PlainPad_Color_Green
        "orange" -> R.style.ThemeOverlay_PlainPad_Color_Orange
        "red" -> R.style.ThemeOverlay_PlainPad_Color_Red
        "pink" -> R.style.ThemeOverlay_PlainPad_Color_Pink
        else -> 0
    }

    fun apply(activity: Activity, colorId: String) {
        if (colorId == "system") {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                DynamicColors.applyToActivityIfAvailable(activity)
            }
            return
        }
        val overlay = overlayFor(colorId)
        if (overlay != 0) {
            activity.theme.applyStyle(overlay, true)
        }
    }

    fun isDynamicColorAvailable(context: Context): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                DynamicColors.isDynamicColorAvailable()
    }

    /**
     * 仅设置状态栏/导航栏图标颜色，不再去设置背景色（因为已经被 View 接管）
     */
    fun applySystemBars(activity: Activity) {
        val window = activity.window
        val primary = resolvePrimary(activity)
        val isLight = isColorLight(primary)

        val controller = WindowInsetsControllerCompat(window, window.decorView)
        // 如果状态栏背景是深色，图标就显示白色；如果是浅色，图标就显示黑色
        controller.isAppearanceLightStatusBars = isLight
        controller.isAppearanceLightNavigationBars = isLight
    }

    // 判断颜色是否为浅色
    private fun isColorLight(color: Int): Boolean {
        val darkness = 1 - (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255
        return darkness < 0.5
    }

    private fun resolvePrimary(activity: Activity): Int {
        val tv = TypedValue()
        return if (activity.theme.resolveAttribute(
                com.google.android.material.R.attr.colorPrimary, tv, true
            )
        ) {
            if (tv.resourceId != 0) ContextCompat.getColor(activity, tv.resourceId)
            else tv.data
        } else {
            0xFF6750A4.toInt()
        }
    }
}