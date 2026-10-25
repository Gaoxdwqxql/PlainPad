package com.sunwenqing.plainpad

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.WindowCompat
import com.sunwenqing.plainpad.databinding.ActivityHelpBinding

class HelpActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHelpBinding
    private lateinit var prefs: Prefs

    override fun onCreate(savedInstanceState: Bundle?) {
        prefs = Prefs(this)
        ThemeColorHelper.apply(this, prefs.themeColor)
        AppCompatDelegate.setDefaultNightMode(prefs.themeMode)
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityHelpBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // ★ 设置纯色填充高度
        binding.statusBarFill.setStatusBarHeight()

        ThemeColorHelper.applySystemBars(this)
        binding.helpToolbar.setNavigationOnClickListener { finish() }
    }
}