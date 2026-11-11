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

class AboutActivity : EinkActivity() {

    private lateinit var binding: ActivityAboutBinding
    private lateinit var prefs: Prefs

    private var versionClickCount = 0
    private var lastVersionClickTime = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        prefs = Prefs(this)
        ThemeColorHelper.apply(this, prefs.themeColor)
        AppCompatDelegate.setDefaultNightMode(prefs.themeMode)
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.statusBarFill.setStatusBarHeight()

        ThemeColorHelper.applySystemBars(this)
        binding.aboutToolbar.setNavigationOnClickListener { finish() }
        binding.tvAboutVersion.text = getString(R.string.about_version, appVersionName())
        binding.tvAboutVersion.setOnClickListener { onVersionClicked() }
        binding.btnSetDefault.setOnClickListener { openDefaultAppsSettings() }

        binding.tvLicense.text = getString(R.string.about_license_text)
        binding.tvMadeWith.text = getString(R.string.about_made_with)

        setupLinks()
    }

    // ---------------- 链接 + 彩蛋 ----------------

    private fun setupLinks() {
        binding.rowWebsite.setOnClickListener {
            openUrl("https://sunwe.dpdns.org/app/PlainPad/")
        }
        binding.rowWebsite.setOnLongClickListener {
            val faces = listOf("(・∀・)", "(￣▽￣)", "┬─┬ ノ( ゜-゜ノ)", "(๑•̀ㅂ•́)و✧")
            AppTips.showShort(this, faces.random())
            true
        }

        binding.rowGithub.setOnClickListener {
            openUrl("https://github.com/Gaoxdwqxql/PlainPad")
        }
        binding.rowGithub.setOnLongClickListener {
            AppTips.showShort(this, "⭐ 点个 Star 吧")
            true
        }

        binding.rowIssues.setOnClickListener {
            openUrl("https://github.com/Gaoxdwqxql/PlainPad/issues")
        }
        binding.rowIssues.setOnLongClickListener {
            AppTips.showShort(this, "写清楚复现步骤，我会看")
            true
        }
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Exception) {
            AppTips.showShort(this, getString(R.string.about_link_failed))
        }
    }

    // ---------------- 开发者选项入口 ----------------

    private fun onVersionClicked() {
        val now = System.currentTimeMillis()
        if (now - lastVersionClickTime > 1500L) {
            versionClickCount = 0
        }
        lastVersionClickTime = now
        versionClickCount++

        when (versionClickCount) {
            in 4..6 -> {
                val remain = 7 - versionClickCount
                AppTips.showShort(
                    this,
                    getString(R.string.about_version_egg_hint, remain)
                )
            }
            7 -> {
                versionClickCount = 0
                prefs.devOptionsUnlocked = true
                startActivity(Intent(this, DeveloperOptionsActivity::class.java))
            }
        }
    }

    // ---------------- 版本 / 默认应用 ----------------

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
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.fromParts("package", packageName, null))
        }
        try {
            startActivity(intent)
        } catch (_: Exception) {}
    }
}
