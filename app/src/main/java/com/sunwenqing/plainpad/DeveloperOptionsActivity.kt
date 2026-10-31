package com.sunwenqing.plainpad

import android.app.ActivityManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.core.view.WindowCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sunwenqing.plainpad.databinding.ActivityDeveloperOptionsBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DeveloperOptionsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDeveloperOptionsBinding
    private lateinit var prefs: Prefs

    override fun onCreate(savedInstanceState: Bundle?) {
        prefs = Prefs(this)
        ThemeColorHelper.apply(this, prefs.themeColor)
        AppCompatDelegate.setDefaultNightMode(prefs.themeMode)
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityDeveloperOptionsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.statusBarFill.setStatusBarHeight()
        ThemeColorHelper.applySystemBars(this)

        binding.devToolbar.setNavigationOnClickListener { finish() }

        setupShortcutSection()
        setupAppInfoSection()
        setupStorageSection()
        setupCrashSection()
        setupTestDocSection()
        setupFontSection()
        setupQuickSwitchSection()
        setupIntentSection()
        setupDataSection()
    }

    // ---------------- 设置页入口 ----------------

    private fun setupShortcutSection() {
        binding.switchDevShortcut.isChecked = prefs.devOptionsUnlocked
        binding.rowDevShortcut.setOnClickListener {
            binding.switchDevShortcut.isChecked = !binding.switchDevShortcut.isChecked
        }
        binding.switchDevShortcut.setOnCheckedChangeListener { _, checked ->
            prefs.devOptionsUnlocked = checked
            if (!checked) {
                AppTips.showLong(this, R.string.dev_disable_shortcut_done)
            }
        }
    }

    // ---------------- 应用与设备信息 ----------------

    private fun setupAppInfoSection() {
        binding.rowAppInfo.setOnClickListener { showAppInfo() }
        binding.rowDeviceInfo.setOnClickListener { showDeviceInfo() }
    }

    private fun showAppInfo() {
        val pm = packageManager
        val pkgInfo = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, 0)
            }
        } catch (_: Exception) { null }

        val ai = applicationInfo

        val sb = StringBuilder()
        sb.append("Package: ").append(packageName).append('\n')
        sb.append("Version name: ").append(pkgInfo?.versionName ?: "?").append('\n')
        sb.append("Version code: ").append(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pkgInfo?.longVersionCode ?: 0L
            } else {
                @Suppress("DEPRECATION")
                (pkgInfo?.versionCode ?: 0).toLong()
            }
        ).append('\n')
        sb.append("First install: ").append(formatTime(pkgInfo?.firstInstallTime ?: 0L)).append('\n')
        sb.append("Last update: ").append(formatTime(pkgInfo?.lastUpdateTime ?: 0L)).append('\n')
        sb.append("Debug build: ").append(
            (ai.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        ).append('\n')
        sb.append("Min SDK: ").append(ai.minSdkVersion).append('\n')
        sb.append("Target SDK: ").append(ai.targetSdkVersion).append('\n')
        sb.append("Running on API: ").append(Build.VERSION.SDK_INT).append('\n')

        showCopyDialog(getString(R.string.dev_app_info), sb.toString())
    }

    private fun showDeviceInfo() {
        val dm = resources.displayMetrics
        val sb = StringBuilder()
        sb.append("Manufacturer: ").append(Build.MANUFACTURER).append('\n')
        sb.append("Brand: ").append(Build.BRAND).append('\n')
        sb.append("Model: ").append(Build.MODEL).append('\n')
        sb.append("Device: ").append(Build.DEVICE).append('\n')
        sb.append("Product: ").append(Build.PRODUCT).append('\n')
        sb.append("Android: ").append(Build.VERSION.RELEASE)
            .append(" (API ").append(Build.VERSION.SDK_INT).append(")\n")
        sb.append("ABI: ").append(Build.SUPPORTED_ABIS.joinToString(", ")).append('\n')
        sb.append("Display: ").append(dm.widthPixels).append("×").append(dm.heightPixels)
            .append(" @ ").append(dm.densityDpi).append("dpi\n")
        sb.append("Density: ").append(dm.density).append('\n')
        sb.append("Font scale: ").append(resources.configuration.fontScale).append('\n')
        sb.append("Dark mode: ").append(
            if ((resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                == android.content.res.Configuration.UI_MODE_NIGHT_YES
            ) "yes" else "no"
        ).append('\n')
        sb.append("Locale: ").append(Locale.getDefault().toLanguageTag()).append('\n')

        showCopyDialog(getString(R.string.dev_device_info), sb.toString())
    }

    // ---------------- 存储与内存 ----------------

    private fun setupStorageSection() {
        binding.rowStorageInfo.setOnClickListener { showStorageInfo() }
        binding.rowMemoryInfo.setOnClickListener { showMemoryInfo() }
        binding.rowClearCache.setOnClickListener {
            confirmAction(R.string.dev_confirm_clear_cache) {
                cacheDir.deleteRecursively()
                cacheDir.mkdirs()
                AppTips.showShort(this, R.string.dev_action_done)
            }
        }
    }

    private fun showStorageInfo() {
        val sb = StringBuilder()
        sb.append("filesDir: ").append(dirSize(filesDir)).append('\n')
        sb.append("  drafts: ").append(dirSize(DraftManager.draftsDir(this))).append('\n')
        sb.append("  fonts: ").append(dirSize(File(filesDir, "fonts"))).append('\n')
        sb.append("cacheDir: ").append(dirSize(cacheDir)).append('\n')
        sb.append("noBackupFilesDir: ").append(dirSize(noBackupFilesDir)).append('\n')
        sb.append("SharedPreferences: ").append(
            File(applicationInfo.dataDir, "shared_prefs").let { dirSize(it) }
        ).append('\n')
        showCopyDialog(getString(R.string.dev_storage_info), sb.toString())
    }

    private fun showMemoryInfo() {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        val runtime = Runtime.getRuntime()
        val sb = StringBuilder()
        sb.append("Total RAM: ").append(formatBytes(mi.totalMem)).append('\n')
        sb.append("Available RAM: ").append(formatBytes(mi.availMem)).append('\n')
        sb.append("Low memory: ").append(mi.lowMemory).append('\n')
        sb.append("Low mem threshold: ").append(formatBytes(mi.threshold)).append('\n')
        sb.append("Heap limit: ").append(formatBytes(runtime.maxMemory())).append('\n')
        sb.append("Heap used: ").append(
            formatBytes(runtime.totalMemory() - runtime.freeMemory())
        ).append('\n')
        sb.append("Heap allocated: ").append(formatBytes(runtime.totalMemory())).append('\n')
        sb.append("Heap free: ").append(formatBytes(runtime.freeMemory())).append('\n')
        showCopyDialog(getString(R.string.dev_memory_info), sb.toString())
    }

    // ---------------- 崩溃模拟 ----------------

    private fun setupCrashSection() {
        binding.rowCrashMain.setOnClickListener {
            confirmAction(R.string.dev_confirm_crash) {
                throw RuntimeException("Simulated crash from Developer Options")
            }
        }

        binding.rowCrashBackground.setOnClickListener {
            confirmAction(R.string.dev_confirm_crash) {
                Thread {
                    throw RuntimeException("Simulated background crash")
                }.start()
            }
        }

        binding.rowCrashOom.setOnClickListener {
            confirmAction(R.string.dev_confirm_crash) {
                throw OutOfMemoryError("Simulated OOM from Developer Options")
            }
        }

        binding.rowCrashAnr.setOnClickListener {
            confirmAction(R.string.dev_confirm_crash) {
                AppTips.showShort(this, R.string.dev_anr_triggered)
                binding.root.postDelayed({
                    Thread.sleep(15_000)
                }, 500)
            }
        }

        binding.rowViewCrashLogs.setOnClickListener { showCrashLogs() }

        binding.rowClearCrashLogs.setOnClickListener {
            confirmAction(R.string.dev_confirm_clear_crash_logs) {
                CrashLogger.clear(this)
                AppTips.showShort(this, R.string.dev_action_done)
            }
        }
    }

    /** 崩溃日志列表：按时间倒序，点条目看全文，可一键复制全部 */
    private fun showCrashLogs() {
        val files = CrashLogger.list(this)
        if (files.isEmpty()) {
            AppTips.showShort(this, R.string.dev_empty_crash_logs)
            return
        }

        val fmt = SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault())
        val labels = files.map { fmt.format(Date(it.lastModified())) }.toTypedArray()

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dev_view_crash_logs)
            .setItems(labels) { dialog, which ->
                dialog.dismiss()
                showCrashLogDetail(files[which])
            }
            .setNeutralButton(R.string.dev_copy) { _, _ ->
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(
                    ClipData.newPlainText("PlainPad crash logs", CrashLogger.readAll(this))
                )
                AppTips.showShort(this, R.string.dev_copied)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showCrashLogDetail(file: File) {
        val text = runCatching { file.readText(Charsets.UTF_8) }.getOrDefault("")
        showCopyDialog(getString(R.string.dev_view_crash_logs), text)
    }

    // ---------------- 测试文档 ----------------

    private fun setupTestDocSection() {
        binding.rowTest1mb.setOnClickListener {
            generateTestDoc(1 * 1024 * 1024L, singleLine = false, chinese = false)
        }
        binding.rowTest5mb.setOnClickListener {
            generateTestDoc(5 * 1024 * 1024L, singleLine = true, chinese = false)
        }
        binding.rowTest20mb.setOnClickListener {
            generateTestDoc(20 * 1024 * 1024L, singleLine = true, chinese = false)
        }
        binding.rowTest500kbCn.setOnClickListener {
            generateTestDoc(500 * 1024L, singleLine = false, chinese = true)
        }
        binding.rowTestCrlf.setOnClickListener {
            generateTestDocWithEol(256 * 1024L, "\r\n")
        }
        binding.rowTestMixed.setOnClickListener {
            generateTestDocWithEol(256 * 1024L, "mixed")
        }
    }

    private fun generateTestDoc(size: Long, singleLine: Boolean, chinese: Boolean) {
        val intent = Intent(this, MainActivity::class.java).apply {
            action = MainActivity.ACTION_GENERATE_TEST_DOCUMENT
            putExtra(MainActivity.EXTRA_TEST_SIZE, size)
            putExtra(MainActivity.EXTRA_TEST_SINGLE_LINE, singleLine)
            putExtra(MainActivity.EXTRA_TEST_CHINESE, chinese)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivity(intent)
        finish()
    }

    private fun generateTestDocWithEol(size: Long, eol: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            action = MainActivity.ACTION_GENERATE_TEST_DOCUMENT
            putExtra(MainActivity.EXTRA_TEST_SIZE, size)
            putExtra(MainActivity.EXTRA_TEST_SINGLE_LINE, false)
            putExtra(MainActivity.EXTRA_TEST_CHINESE, false)
            putExtra(MainActivity.EXTRA_TEST_EOL, eol)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivity(intent)
        finish()
    }

    // ---------------- 字体调试 ----------------

    private fun setupFontSection() {
        binding.rowFontList.setOnClickListener { showFontList() }
        binding.rowFontClear.setOnClickListener {
            confirmAction(R.string.dev_confirm_clear_fonts) {
                prefs.customFonts.forEach { f ->
                    if (f.fileName.isNotEmpty()) {
                        prefs.fontFile(f.fileName).delete()
                    }
                }
                prefs.customFonts = emptyList()
                prefs.editorFontFamily = "monospace"
                AppTips.showShort(this, R.string.dev_action_done)
            }
        }
    }

    private fun showFontList() {
        val fonts = prefs.customFonts
        if (fonts.isEmpty()) {
            AppTips.showShort(this, R.string.dev_empty_fonts)
            return
        }
        val sb = StringBuilder()
        sb.append("Current: ").append(prefs.editorFontFamily).append("\n\n")
        fonts.forEachIndexed { i, f ->
            sb.append("#").append(i + 1).append(" ").append(f.displayName).append('\n')
            sb.append("  fileName: ").append(f.fileName.ifEmpty { "-" }).append('\n')
            sb.append("  uri: ").append(f.uri.ifEmpty { "-" }).append('\n')
            if (f.fileName.isNotEmpty()) {
                val file = prefs.fontFile(f.fileName)
                sb.append("  size: ").append(formatBytes(file.length())).append('\n')
                sb.append("  exists: ").append(file.exists()).append('\n')
            }
            sb.append('\n')
        }
        showCopyDialog(getString(R.string.dev_font_list), sb.toString())
    }

    // ---------------- 快速切换 ----------------

    private fun setupQuickSwitchSection() {
        binding.rowQuickThemeColor.setOnClickListener { showQuickThemeColor() }
        binding.rowQuickIcon.setOnClickListener { showQuickIcon() }
        binding.rowQuickLanguage.setOnClickListener { showQuickLanguage() }
    }

    private fun showQuickThemeColor() {
        val ids = arrayOf("system", "purple", "blue", "indigo", "teal", "green", "orange", "red", "pink")
        val names = ids.map { themeColorName(it) }.toTypedArray()
        val current = ids.indexOf(prefs.themeColor).coerceAtLeast(0)

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dev_quick_theme_color)
            .setSingleChoiceItems(names, current) { dialog, which ->
                dialog.dismiss()
                val picked = ids[which]
                if (picked != prefs.themeColor) {
                    prefs.themeColor = picked
                    AppTips.showShort(this, getString(R.string.dev_quick_switched, names[which]))
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showQuickIcon() {
        val ids = arrayOf("purple", "blue", "indigo", "teal", "green", "orange", "red", "pink")
        val names = ids.map { iconName(it) }.toTypedArray()

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dev_quick_icon)
            .setItems(names) { dialog, which ->
                dialog.dismiss()
                val picked = ids[which]
                prefs.themeColor = picked
                IconSwitcher.applyIfNeeded(this, picked)
                AppTips.showShort(this, getString(R.string.dev_quick_switched, names[which]))
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showQuickLanguage() {
        val options = SettingsCatalog.LANGUAGES
        val names = options.map { getString(it.second) }.toTypedArray()
        val locales = AppCompatDelegate.getApplicationLocales()
        val primary = locales[0]?.language.orEmpty()
        val current = if (locales.isEmpty()) 0
        else options.indexOfFirst { it.first.isNotEmpty() && it.first == primary }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dev_quick_language)
            .setSingleChoiceItems(names, current) { dialog, which ->
                dialog.dismiss()
                val tag = options[which].first
                val picked = if (tag.isEmpty()) {
                    LocaleListCompat.getEmptyLocaleList()
                } else {
                    LocaleListCompat.forLanguageTags(tag)
                }
                AppCompatDelegate.setApplicationLocales(picked)
                AppTips.showShort(this, getString(R.string.dev_quick_switched, names[which]))
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ---------------- Intent 模拟与只读测试 ----------------

    private fun setupIntentSection() {
        binding.rowIntentView.setOnClickListener { simulateIntent(Intent.ACTION_VIEW) }
        binding.rowIntentEdit.setOnClickListener { simulateIntent(Intent.ACTION_EDIT) }
        binding.rowIntentSend.setOnClickListener { simulateShare() }
        binding.rowReadOnly.setOnClickListener { openReadOnlyDoc() }
    }

    private fun simulateIntent(action: String) {
        val sample = "PlainPad\nDeveloper intent test\nAction: $action\nTime: ${System.currentTimeMillis()}"
        val intent = Intent(this, MainActivity::class.java).apply {
            this.action = action
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, sample)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        try {
            startActivity(intent)
            finish()
        } catch (e: Exception) {
            AppTips.showLong(this, getString(R.string.dev_intent_failed, e.message ?: ""))
        }
    }

    private fun simulateShare() {
        val text = "Shared from Developer Options at ${formatTime(System.currentTimeMillis())}"
        val intent = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_SEND
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivity(intent)
        finish()
    }

    private fun openReadOnlyDoc() {
        val sample = buildString {
            append("This is a read-only test document.\n")
            append("It will not be editable, and any edit attempt should be blocked.\n\n")
            repeat(50) { i ->
                append("Line ").append(i + 1).append(": test content\n")
            }
        }
        val intent = Intent(this, MainActivity::class.java).apply {
            action = MainActivity.ACTION_OPEN_READ_ONLY_TEST
            putExtra(MainActivity.EXTRA_TEST_CONTENT, sample)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivity(intent)
        finish()
    }

    // ---------------- 数据操作 ----------------

    private fun setupDataSection() {
        binding.rowViewPrefs.setOnClickListener { showPrefsContent() }
        binding.rowViewDrafts.setOnClickListener { showDraftsList() }

        binding.rowClearDrafts.setOnClickListener {
            confirmAction(R.string.dev_confirm_clear_drafts) {
                DraftManager.clearAll(this)
                AppTips.showShort(this, R.string.dev_action_done)
            }
        }

        binding.rowClearRecent.setOnClickListener {
            confirmAction(R.string.dev_confirm_clear_recent) {
                prefs.clearRecentFiles()
                AppTips.showShort(this, R.string.dev_action_done)
            }
        }

        binding.rowClearTabs.setOnClickListener {
            confirmAction(R.string.dev_confirm_clear_tabs) {
                prefs.clearSavedTabs()
                AppTips.showShort(this, R.string.dev_action_done)
            }
        }

        binding.rowResetPrefs.setOnClickListener {
            confirmAction(R.string.dev_confirm_reset_prefs) {
                val sp = getSharedPreferences("plainpad_prefs", Context.MODE_PRIVATE)
                sp.edit().clear().commit()
                AppTips.showShort(this, R.string.dev_action_done)
            }
        }
    }

    private fun showPrefsContent() {
        val sp = getSharedPreferences("plainpad_prefs", Context.MODE_PRIVATE)
        val all = sp.all
        if (all.isEmpty()) {
            AppTips.showShort(this, R.string.dev_empty_prefs)
            return
        }
        val sb = StringBuilder()
        all.entries.sortedBy { it.key }.forEach { (k, v) ->
            val value = v.toString()
            val shown = if (value.length > 120) value.take(120) + "…" else value
            sb.append(k).append(" = ").append(shown).append('\n')
        }
        showCopyDialog(getString(R.string.dev_view_prefs), sb.toString())
    }

    private fun showDraftsList() {
        val drafts = DraftManager.listAll(this)
        if (drafts.isEmpty()) {
            AppTips.showShort(this, R.string.dev_empty_drafts)
            return
        }
        val sb = StringBuilder()
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        drafts.forEach { d ->
            val ts = fmt.format(Date(d.savedAt))
            val textFile = File(DraftManager.draftsDir(this), d.draftId + ".txt")
            sb.append(d.displayName).append('\n')
            sb.append("  draftId: ").append(d.draftId).append('\n')
            sb.append("  savedAt: ").append(ts).append('\n')
            sb.append("  size: ").append(formatBytes(textFile.length())).append('\n')
            sb.append('\n')
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dev_view_drafts)
            .setMessage(sb.toString())
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    // ---------------- 通用 ----------------

    private fun showCopyDialog(title: String, content: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setMessage(content)
            .setPositiveButton(android.R.string.ok, null)
            .setNeutralButton(R.string.dev_copy) { _, _ ->
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText(title, content))
                AppTips.showShort(this, R.string.dev_copied)
            }
            .show()
    }

    private fun confirmAction(messageRes: Int, action: () -> Unit) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dev_confirm_title)
            .setMessage(messageRes)
            .setPositiveButton(R.string.ok) { _, _ -> action() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun themeColorName(id: String): String = when (id) {
        "system" -> getString(R.string.theme_color_system)
        "blue" -> getString(R.string.theme_color_blue)
        "indigo" -> getString(R.string.theme_color_indigo)
        "teal" -> getString(R.string.theme_color_teal)
        "green" -> getString(R.string.theme_color_green)
        "orange" -> getString(R.string.theme_color_orange)
        "red" -> getString(R.string.theme_color_red)
        "pink" -> getString(R.string.theme_color_pink)
        else -> getString(R.string.theme_color_purple)
    }

    private fun iconName(id: String): String = when (id) {
        "blue" -> getString(R.string.theme_color_blue)
        "indigo" -> getString(R.string.theme_color_indigo)
        "teal" -> getString(R.string.theme_color_teal)
        "green" -> getString(R.string.theme_color_green)
        "orange" -> getString(R.string.theme_color_orange)
        "red" -> getString(R.string.theme_color_red)
        "pink" -> getString(R.string.theme_color_pink)
        else -> getString(R.string.theme_color_purple)
    }

    private fun formatTime(ms: Long): String {
        if (ms <= 0L) return "-"
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(ms))
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb)
        return String.format(Locale.US, "%.2f GB", mb / 1024.0)
    }

    private fun dirSize(dir: File): String {
        if (!dir.exists()) return "0 B"
        var total = 0L
        dir.walkTopDown().forEach { if (it.isFile) total += it.length() }
        return formatBytes(total)
    }
}