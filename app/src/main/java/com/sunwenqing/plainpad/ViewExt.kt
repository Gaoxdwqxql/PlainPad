package com.sunwenqing.plainpad

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * 动态设置顶部 View 的高度为系统状态栏高度，用于纯色填充
 */
fun View.setStatusBarHeight() {
    ViewCompat.setOnApplyWindowInsetsListener(this) { view: View, windowInsets: WindowInsetsCompat ->
        val insets = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars())
        val lp = view.layoutParams
        if (lp.height != insets.top) {
            lp.height = insets.top
            view.layoutParams = lp
            view.requestLayout()
        }
        windowInsets
    }
    ViewCompat.requestApplyInsets(this)
}

/**
 * 动态设置底部 View 的高度为系统导航栏高度
 */
fun View.setNavigationBarHeight() {
    ViewCompat.setOnApplyWindowInsetsListener(this) { view: View, windowInsets: WindowInsetsCompat ->
        val insets = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars())
        val lp = view.layoutParams
        if (lp.height != insets.bottom) {
            lp.height = insets.bottom
            view.layoutParams = lp
            view.requestLayout()
        }
        windowInsets
    }
    ViewCompat.requestApplyInsets(this)
}