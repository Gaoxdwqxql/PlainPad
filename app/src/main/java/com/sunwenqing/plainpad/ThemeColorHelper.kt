package com.sunwenqing.plainpad

import android.app.Activity
import android.content.Context
import android.os.Build
import android.util.TypedValue
import androidx.annotation.StyleRes
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.material.color.DynamicColors

/**
 * 主题色应用工具。
 * - apply()：在 Activity.onCreate() 的 super.onCreate() 之前调用
 * - applySystemBars()：在 setContentView() 之后调用，让状态栏/导航栏跟随主题色
 */
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
     * 让系统状态栏 / 导航栏颜色跟随主题色。
     * 必须在 setContentView() 之后调用，因为此时才能取到 ThemeOverlay 生效后的 colorPrimary。
     */
    fun applySystemBars(activity: Activity) {
        val window = activity.window
        val primary = resolvePrimary(activity)

        @Suppress("DEPRECATION")
        window.statusBarColor = primary

        @Suppress("DEPRECATION")
        window.navigationBarColor = primary

        // 状态栏图标用白色（在深色主题色背景上更清晰）
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false
    }

    /** 从当前主题解析 colorPrimary */
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