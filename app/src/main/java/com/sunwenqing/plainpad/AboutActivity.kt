package com.sunwenqing.plainpad

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.WindowCompat
import com.sunwenqing.plainpad.databinding.ActivityAboutBinding

class AboutActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAboutBinding
    private lateinit var prefs: Prefs

    override fun onCreate(savedInstanceState: Bundle?) {
        prefs = Prefs(this)
        ThemeColorHelper.apply(this, prefs.themeColor)
        AppCompatDelegate.setDefaultNightMode(prefs.themeMode)
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // ★ 设置纯色填充高度
        binding.statusBarFill.setStatusBarHeight()

        ThemeColorHelper.applySystemBars(this)
        binding.aboutToolbar.setNavigationOnClickListener { finish() }
        binding.tvAboutVersion.text = getString(R.string.about_version, appVersionName())
        binding.btnSetDefault.setOnClickListener { openDefaultAppsSettings() }
    }

    private fun appVersionName(): String {
        return try {
            val pm = packageManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0)).versionName ?: "1.0"
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, 0).versionName ?: "1.0"
            }
        } catch (_: Exception) { "1.0" }
    }

    private fun openDefaultAppsSettings() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(Uri.fromParts("package", packageName, null))
        }
        try {
            startActivity(intent)
        } catch (_: Exception) {}
    }
}