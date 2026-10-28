package com.sunwenqing.plainpad

import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.sunwenqing.plainpad.databinding.ActivityMainBinding

/**
 * 沉浸模式控制器。
 * 负责隐藏/恢复顶栏、标签栏、状态栏、系统栏，以及控制退出按钮。
 */
class ImmersiveModeController(
    private val activity: AppCompatActivity,
    private val binding: ActivityMainBinding,
    private val prefs: Prefs,
    private val canShowLineNumbers: () -> Boolean
) {

    var isImmersive: Boolean = false
        private set

    fun apply(on: Boolean) {
        if (on == isImmersive) return
        isImmersive = on
        val window = activity.window

        if (on) {
            binding.statusBarFill.visibility = View.GONE
            binding.navigationBarFill.visibility = View.GONE
            binding.topBar.visibility = View.GONE
            binding.tabScroll.visibility = View.GONE
            binding.statusBar.visibility = View.GONE
            binding.tvLineNumbers.visibility = View.GONE
            binding.btnExitImmersive.visibility = View.VISIBLE

            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowInsetsControllerCompat(window, window.decorView).apply {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            binding.statusBarFill.visibility = View.VISIBLE
            binding.navigationBarFill.visibility = View.VISIBLE
            binding.topBar.visibility = View.VISIBLE
            binding.tabScroll.visibility = View.VISIBLE
            binding.statusBar.visibility = View.VISIBLE
            binding.tvLineNumbers.visibility =
                if (canShowLineNumbers()) View.VISIBLE else View.GONE
            binding.btnExitImmersive.visibility = View.GONE

            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowInsetsControllerCompat(window, window.decorView)
                .show(WindowInsetsCompat.Type.systemBars())
            ThemeColorHelper.applySystemBars(activity)
        }
    }
}