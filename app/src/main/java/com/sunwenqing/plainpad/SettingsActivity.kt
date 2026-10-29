package com.sunwenqing.plainpad

import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sunwenqing.plainpad.databinding.ActivitySettingsBinding
import com.sunwenqing.plainpad.databinding.DialogChoiceBinding
import com.sunwenqing.plainpad.databinding.DialogChoiceItemBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefs: Prefs

    private var suppressStatusListener = false

    private var editingExpanded = true
    private var saveExpanded = true
    private var appearanceExpanded = true
    private var tabsExpanded = true
    private var otherExpanded = true
    private var rulesExpanded = false

    private val importFontLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) importFont(uri)
    }

    private val exportIniLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        exportIni(uri)
    }

    private val importIniLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        importIni(uri)
    }

    private val searchLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val key = result.data?.getStringExtra(SettingsSearchActivity.EXTRA_KEY)
            if (!key.isNullOrEmpty()) locateSetting(key)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        prefs = Prefs(this)

        FontManager.migrateLegacy(this, prefs)

        ThemeColorHelper.apply(this, prefs.themeColor)
        AppCompatDelegate.setDefaultNightMode(prefs.themeMode)
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.statusBarFill.setStatusBarHeight()

        ThemeColorHelper.applySystemBars(this)

        binding.btnBack.setOnClickListener { finish() }
        binding.btnSearch.setOnClickListener {
            searchLauncher.launch(Intent(this, SettingsSearchActivity::class.java))
        }

        setupSections()
        setupScrollBar()

        setupFontSize()
        setupSwitches()
        setupTheme()
        setupThemeColor()
        setupEditorTheme()
        setupEditorFont()
        setupLanguage()
        setupImportFont()
        setupCopyFontSwitch()
        setupStartupMode()
        setupPlusBehavior()
        setupStatusBarItems()
        setupAutoSave()
        setupUtf8Bom()
        setupReplaceRules()
        setupOtherSection()
    }

    override fun onResume() {
        super.onResume()
        updateEditorThemeLabel()
        updateEditorFontLabel()
        updatePlusHint(prefs.plusReuseEmpty)
        syncStatusBarCheckboxes()
        syncAutoSaveUI()
        updateThemeColorLabel()
        updateLanguageLabel()
        binding.switchCopyFont.isChecked = prefs.copyFontToPrivate
        binding.switchUtf8Bom.isChecked = prefs.utf8Bom
        updateDevOptionsRow()
    }

    private fun setupSections() {
        bindSection(binding.headerEditing, binding.contentEditing, binding.ivExpandEditing, { editingExpanded }, { editingExpanded = it })
        bindSection(binding.headerSave, binding.contentSave, binding.ivExpandSave, { saveExpanded }, { saveExpanded = it })
        bindSection(binding.headerAppearance, binding.contentAppearance, binding.ivExpandAppearance, { appearanceExpanded }, { appearanceExpanded = it })
        bindSection(binding.headerTabs, binding.contentTabs, binding.ivExpandTabs, { tabsExpanded }, { tabsExpanded = it })
        bindSection(binding.headerOther, binding.contentOther, binding.ivExpandOther, { otherExpanded }, { otherExpanded = it })

        applySectionState(binding.contentEditing, binding.ivExpandEditing, editingExpanded)
        applySectionState(binding.contentSave, binding.ivExpandSave, saveExpanded)
        applySectionState(binding.contentAppearance, binding.ivExpandAppearance, appearanceExpanded)
        applySectionState(binding.contentTabs, binding.ivExpandTabs, tabsExpanded)
        applySectionState(binding.contentOther, binding.ivExpandOther, otherExpanded)
    }

    private fun bindSection(header: View, content: View, arrow: View, isExpanded: () -> Boolean, setExpanded: (Boolean) -> Unit) {
        header.setOnClickListener {
            val newState = !isExpanded()
            setExpanded(newState)
            applySectionState(content, arrow, newState)
        }
    }

    private fun applySectionState(content: View, arrow: View, expanded: Boolean) {
        content.visibility = if (expanded) View.VISIBLE else View.GONE
        arrow.animate().rotation(if (expanded) 180f else 0f).setDuration(180).start()
    }

    private fun setupScrollBar() {
        binding.settingsScrollBar.attachScrollView(binding.settingsScroll)
    }

    private fun locateSetting(key: String) {
        editingExpanded = true; saveExpanded = true; appearanceExpanded = true; tabsExpanded = true; otherExpanded = true
        applySectionState(binding.contentEditing, binding.ivExpandEditing, true)
        applySectionState(binding.contentSave, binding.ivExpandSave, true)
        applySectionState(binding.contentAppearance, binding.ivExpandAppearance, true)
        applySectionState(binding.contentTabs, binding.ivExpandTabs, true)
        applySectionState(binding.contentOther, binding.ivExpandOther, true)

        val target = findViewByTag(binding.settingsRoot, "search:$key") ?: return
        binding.settingsScroll.post {
            val y = relativeTop(target, binding.settingsRoot)
            binding.settingsScroll.smoothScrollTo(0, (y - 120).coerceAtLeast(0))
            flashView(target)
        }
    }

    private fun findViewByTag(root: View, tag: String): View? {
        if (root.tag == tag) return root
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                findViewByTag(root.getChildAt(i), tag)?.let { return it }
            }
        }
        return null
    }

    private fun relativeTop(view: View, ancestor: View): Int {
        var y = 0
        var v: View? = view
        while (v != null && v !== ancestor) {
            y += v.top
            v = v.parent as? View
        }
        return y
    }

    private fun flashView(view: View) {
        val original = view.background
        val density = resources.displayMetrics.density
        val highlight = GradientDrawable().apply {
            setColor(MaterialColors.getColor(view, com.google.android.material.R.attr.colorPrimaryContainer))
            cornerRadius = 10 * density
        }
        view.background = highlight
        view.postDelayed({ view.background = original }, 1500)
    }

    private fun setupFontSize() {
        binding.seekFontSize.max = 30
        binding.seekFontSize.progress = prefs.fontSize - 10
        updateFontSizeLabel(prefs.fontSize)
        binding.seekFontSize.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                val size = progress + 10
                updateFontSizeLabel(size)
                if (fromUser) prefs.fontSize = size
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
    }

    private fun updateFontSizeLabel(size: Int) {
        binding.tvFontSizeValue.text = getString(R.string.font_size_label, size)
    }

    private fun setupSwitches() {
        binding.switchWrap.isChecked = prefs.wrap
        binding.switchLineNumbers.isChecked = prefs.showLineNumbers
        binding.switchShowSpecialChars.isChecked = prefs.showSpecialChars
        binding.switchCurrentLine.isChecked = prefs.currentLineHighlight

        binding.switchWrap.setOnCheckedChangeListener { _, checked -> prefs.wrap = checked }
        binding.switchLineNumbers.setOnCheckedChangeListener { _, checked -> prefs.showLineNumbers = checked }
        binding.switchShowSpecialChars.setOnCheckedChangeListener { _, checked -> prefs.showSpecialChars = checked }
        binding.switchCurrentLine.setOnCheckedChangeListener { _, checked -> prefs.currentLineHighlight = checked }

        binding.rowWrap.setOnClickListener { binding.switchWrap.isChecked = !binding.switchWrap.isChecked }
        binding.rowLineNumbers.setOnClickListener { binding.switchLineNumbers.isChecked = !binding.switchLineNumbers.isChecked }
        binding.rowShowSpecialChars.setOnClickListener { binding.switchShowSpecialChars.isChecked = !binding.switchShowSpecialChars.isChecked }
        binding.rowCurrentLine.setOnClickListener { binding.switchCurrentLine.isChecked = !binding.switchCurrentLine.isChecked }
    }

    private fun setupTheme() {
        when (prefs.themeMode) {
            AppCompatDelegate.MODE_NIGHT_NO -> binding.radioLight.isChecked = true
            AppCompatDelegate.MODE_NIGHT_YES -> binding.radioDark.isChecked = true
            else -> binding.radioSystem.isChecked = true
        }

        binding.radioTheme.setOnCheckedChangeListener { _, checkedId ->
            val mode = when (checkedId) {
                R.id.radioLight -> AppCompatDelegate.MODE_NIGHT_NO
                R.id.radioDark -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
            if (mode != prefs.themeMode) {
                prefs.themeMode = mode
                AppCompatDelegate.setDefaultNightMode(mode)
            }
        }
    }

    private fun setupThemeColor() {
        updateThemeColorLabel()
        binding.rowThemeColor.setOnClickListener { showThemeColorDialog() }
    }

    private fun updateThemeColorLabel() {
        binding.tvThemeColorValue.text = themeColorName(prefs.themeColor)
        val color = getColorForThemeId(prefs.themeColor)
        val drawable = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
            setStroke((1.5f * resources.displayMetrics.density).toInt(), 0x33000000)
        }
        binding.viewThemeColorPreview.background = drawable
    }

    private fun getColorForThemeId(id: String): Int {
        return when (id) {
            "system" -> MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorPrimary, 0xFF6750A4.toInt())
            "purple" -> ContextCompat.getColor(this, R.color.tc_purple_primary)
            "blue" -> ContextCompat.getColor(this, R.color.tc_blue_primary)
            "indigo" -> ContextCompat.getColor(this, R.color.tc_indigo_primary)
            "teal" -> ContextCompat.getColor(this, R.color.tc_teal_primary)
            "green" -> ContextCompat.getColor(this, R.color.tc_green_primary)
            "orange" -> ContextCompat.getColor(this, R.color.tc_orange_primary)
            "red" -> ContextCompat.getColor(this, R.color.tc_red_primary)
            "pink" -> ContextCompat.getColor(this, R.color.tc_pink_primary)
            else -> 0xFF888888.toInt()
        }
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

    private fun showThemeColorDialog() {
        val ids = arrayOf("system", "purple", "blue", "indigo", "teal", "green", "orange", "red", "pink")
        val names = ids.map { themeColorName(it) }.toTypedArray()
        val current = ids.indexOf(prefs.themeColor).coerceAtLeast(0)
        val dynamicAvailable = ThemeColorHelper.isDynamicColorAvailable(this)

        val b = DialogChoiceBinding.inflate(layoutInflater)
        b.tvDialogTitle.setText(R.string.theme_color_title)

        val dialog = MaterialAlertDialogBuilder(this).setView(b.root).create()

        names.forEachIndexed { i, name ->
            val item = DialogChoiceItemBinding.inflate(layoutInflater, b.choiceContainer, false)
            item.tvOptionText.text = name
            item.ivCheck.visibility = if (i == current) View.VISIBLE else View.INVISIBLE

            val colorPreview = View(this)
            val size = (18 * resources.displayMetrics.density).toInt()
            val marginEnd = (12 * resources.displayMetrics.density).toInt()
            val lp = android.widget.LinearLayout.LayoutParams(size, size)
            lp.marginEnd = marginEnd
            colorPreview.layoutParams = lp

            val color = getColorForThemeId(ids[i])
            val drawable = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color)
                setStroke((1.5f * resources.displayMetrics.density).toInt(), 0x33000000)
            }
            colorPreview.background = drawable

            item.root.addView(colorPreview, item.root.childCount - 1)

            item.root.setOnClickListener {
                val picked = ids[i]
                if (picked == "system" && !dynamicAvailable) {
                    Toast.makeText(this, getString(R.string.theme_color_system_unavailable), Toast.LENGTH_SHORT).show()
                } else if (picked != prefs.themeColor) {
                    prefs.themeColor = picked
                    updateThemeColorLabel()
                    dialog.dismiss()
                    recreate()
                } else {
                    dialog.dismiss()
                }
            }
            b.choiceContainer.addView(item.root)
        }

        dialog.show()
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.9).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun setupLanguage() {
        updateLanguageLabel()
        binding.rowLanguage.setOnClickListener { showLanguageDialog() }
    }

    /** 语言选项：空字符串表示跟随系统 */
    private val languageOptions = SettingsCatalog.LANGUAGES

    private fun updateLanguageLabel() {
        val locales = AppCompatDelegate.getApplicationLocales()
        if (locales.isEmpty()) {
            binding.tvLanguageValue.text = getString(R.string.language_system)
            return
        }
        val primary = locales[0]?.language.orEmpty()
        val hit = languageOptions.firstOrNull { it.first.isNotEmpty() && it.first == primary }
        binding.tvLanguageValue.text = hit?.let { getString(it.second) } ?: primary
    }

    private fun showLanguageDialog() {
        val names = languageOptions.map { getString(it.second) }.toTypedArray()
        val locales = AppCompatDelegate.getApplicationLocales()
        val primary = locales[0]?.language.orEmpty()
        val current = if (locales.isEmpty()) 0
        else languageOptions.indexOfFirst { it.first.isNotEmpty() && it.first == primary }

        showChoiceDialog(R.string.language_title, names, current) { which ->
            val tag = languageOptions[which].first
            val picked = if (tag.isEmpty()) {
                LocaleListCompat.getEmptyLocaleList()
            } else {
                LocaleListCompat.forLanguageTags(tag)
            }
            AppCompatDelegate.setApplicationLocales(picked)
        }
    }

    private fun setupImportFont() {
        binding.rowImportFont.setOnClickListener {
            importFontLauncher.launch("*/*")
        }
    }

    private fun importFont(uri: Uri) {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                FontManager.importFont(this@SettingsActivity, prefs, uri)
            }

            result.onSuccess { font ->
                prefs.editorFontFamily = FontManager.key(font)
                updateEditorFontLabel()
                Toast.makeText(
                    this@SettingsActivity,
                    R.string.import_font_success,
                    Toast.LENGTH_SHORT
                ).show()
            }.onFailure { e ->
                Toast.makeText(
                    this@SettingsActivity,
                    getString(R.string.font_import_fail_reason, e.message ?: ""),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun setupCopyFontSwitch() {
        binding.switchCopyFont.isChecked = prefs.copyFontToPrivate
        binding.rowCopyFont.setOnClickListener {
            binding.switchCopyFont.isChecked = !binding.switchCopyFont.isChecked
        }
        binding.switchCopyFont.setOnCheckedChangeListener { _, checked ->
            prefs.copyFontToPrivate = checked
        }
    }

    private fun setupEditorTheme() {
        updateEditorThemeLabel()
        binding.rowEditorTheme.setOnClickListener { showEditorThemeDialog() }
    }

    private fun updateEditorThemeLabel() {
        val theme = EditorThemes.byId(prefs.editorThemeId)
        binding.tvEditorThemeValue.text = getString(theme.nameRes)
    }

    private fun showEditorThemeDialog() {
        val names = EditorThemes.ALL.map { getString(it.nameRes) }.toTypedArray()
        val current = EditorThemes.ALL.indexOfFirst { it.id == prefs.editorThemeId }.coerceAtLeast(0)
        showChoiceDialog(R.string.settings_editor_theme, names, current) { which ->
            prefs.editorThemeId = EditorThemes.ALL[which].id
            updateEditorThemeLabel()
        }
    }

    private fun setupEditorFont() {
        updateEditorFontLabel()
        binding.rowEditorFont.setOnClickListener {
            startActivity(Intent(this, FontManagerActivity::class.java))
        }
    }

    private fun updateEditorFontLabel() {
        val key = prefs.editorFontFamily

        when {
            FontManager.isUriKey(key) -> {
                val uri = FontManager.uriOfKey(key)?.toString()
                val font = FontManager.list(prefs).firstOrNull { it.uri == uri }
                binding.tvEditorFontValue.text = font?.displayName
                    ?: getString(R.string.font_custom)
            }
            key.startsWith(FontManager.CUSTOM_PREFIX) -> {
                val fileName = FontManager.fileNameOfKey(key)
                val font = FontManager.list(prefs).firstOrNull { it.fileName == fileName }
                binding.tvEditorFontValue.text = font?.displayName
                    ?: getString(R.string.font_custom)
            }
            key == "custom" -> {
                binding.tvEditorFontValue.text = getString(R.string.font_custom)
            }
            else -> {
                binding.tvEditorFontValue.text = getString(EditorThemes.fontNameRes(key))
            }
        }
    }

    private fun setupOtherSection() {
        binding.rowImportIni.setOnClickListener {
            importIniLauncher.launch(arrayOf("text/plain", "text/*", "*/*"))
        }
        binding.rowExportIni.setOnClickListener {
            exportIniLauncher.launch(getString(R.string.ini_export_file_name))
        }
        binding.rowAbout.setOnClickListener {
            startActivity(Intent(this, AboutActivity::class.java))
        }
        binding.rowDevOptions.setOnClickListener {
            startActivity(Intent(this, DeveloperOptionsActivity::class.java))
        }
        updateDevOptionsRow()
    }

    private fun updateDevOptionsRow() {
        val visible = prefs.devOptionsUnlocked
        binding.rowDevOptions.visibility = if (visible) View.VISIBLE else View.GONE
        binding.dividerDevOptions.visibility = if (visible) View.VISIBLE else View.GONE
    }

    private fun exportIni(uri: Uri) {
        try {
            val content = PrefsIni.export(prefs)
            contentResolver.openOutputStream(uri, "wt")?.use {
                it.write(content.toByteArray(Charsets.UTF_8))
                it.flush()
            } ?: throw Exception("cannot open output stream")
            AppTips.showShort(this, R.string.ini_export_success)
        } catch (e: Exception) {
            AppTips.showLong(this, getString(R.string.ini_export_failed, e.message ?: ""))
        }
    }

    private fun importIni(uri: Uri) {
        try {
            val bytes = contentResolver.openInputStream(uri)?.use { readLimited(it) }
                ?: throw Exception("cannot open input stream")

            val content = bytes.toString(Charsets.UTF_8)
            val result = PrefsIni.import(prefs, content)

            result.onSuccess { count ->
                AppTips.showShort(this, getString(R.string.ini_import_success, count))
                binding.root.postDelayed({
                    if (!isFinishing) recreate()
                }, 900)
            }.onFailure { e ->
                AppTips.showLong(this, getString(R.string.ini_import_failed, e.message ?: ""))
            }
        } catch (e: Exception) {
            AppTips.showLong(this, getString(R.string.ini_import_failed, e.message ?: ""))
        }
    }

    private fun readLimited(input: java.io.InputStream): ByteArray {
        val maxBytes = 1024 * 1024
        val out = ByteArrayOutputStream()
        val buf = ByteArray(64 * 1024)
        var total = 0
        var n: Int
        while (input.read(buf).also { n = it } != -1) {
            total += n
            if (total > maxBytes) throw Exception("File too large")
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    private fun showChoiceDialog(titleRes: Int, names: Array<String>, selectedIndex: Int, onPick: (Int) -> Unit) {
        val b = DialogChoiceBinding.inflate(layoutInflater)
        b.tvDialogTitle.setText(titleRes)
        val dialog = MaterialAlertDialogBuilder(this).setView(b.root).create()

        names.forEachIndexed { i, name ->
            val item = DialogChoiceItemBinding.inflate(layoutInflater, b.choiceContainer, false)
            item.tvOptionText.text = name
            item.ivCheck.visibility = if (i == selectedIndex) View.VISIBLE else View.INVISIBLE
            item.root.setOnClickListener {
                onPick(i)
                dialog.dismiss()
            }
            b.choiceContainer.addView(item.root)
        }
        dialog.show()
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.9).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun setupStartupMode() {
        when (prefs.startupMode) {
            1 -> binding.radioStartupNewOnly.isChecked = true
            2 -> binding.radioStartupNewAndRestore.isChecked = true
            else -> binding.radioStartupRestore.isChecked = true
        }

        binding.radioStartupMode.setOnCheckedChangeListener { _, checkedId ->
            val mode = when (checkedId) {
                R.id.radioStartupNewOnly -> 1
                R.id.radioStartupNewAndRestore -> 2
                else -> 0
            }
            prefs.startupMode = mode
        }
    }

    private fun setupPlusBehavior() {
        binding.switchPlusReuse.isChecked = prefs.plusReuseEmpty
        updatePlusHint(prefs.plusReuseEmpty)
        binding.switchPlusReuse.setOnCheckedChangeListener { _, checked ->
            prefs.plusReuseEmpty = checked
            updatePlusHint(checked)
        }
        binding.rowPlusReuse.setOnClickListener {
            binding.switchPlusReuse.isChecked = !binding.switchPlusReuse.isChecked
        }
    }

    private fun updatePlusHint(reuse: Boolean) {
        binding.tvPlusHint.text = getString(if (reuse) R.string.plus_reuse_empty else R.string.plus_always_new)
    }

    private fun statusBoxes(): Array<Pair<MaterialCheckBox, String>> = arrayOf(
        binding.cbCharsSpace to "chars_space",
        binding.cbCharsNoSpace to "chars_nospace",
        binding.cbWords to "words",
        binding.cbLines to "lines",
        binding.cbNonEmptyLines to "nonempty_lines",
        binding.cbMaxLine to "max_line",
        binding.cbLetters to "letters",
        binding.cbInvisible to "invisible"
    )

    private fun setupStatusBarItems() {
        syncStatusBarCheckboxes()
        statusBoxes().forEach { (cb, id) ->
            cb.setOnCheckedChangeListener { _, checked ->
                if (suppressStatusListener) return@setOnCheckedChangeListener
                onStatusItemChanged(id, checked)
            }
        }
    }

    private fun onStatusItemChanged(id: String, checked: Boolean) {
        val current = prefs.statusBarItems.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
        if (checked) {
            if (current.contains(id)) return
            current.add(id)
            while (current.size > 2) current.removeAt(0)
        } else {
            current.remove(id)
        }
        prefs.statusBarItems = current.joinToString(",")
        syncStatusBarCheckboxes()
    }

    private fun syncStatusBarCheckboxes() {
        suppressStatusListener = true
        val current = prefs.statusBarItems.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        statusBoxes().forEach { (cb, id) -> cb.isChecked = current.contains(id) }
        suppressStatusListener = false
    }

    private fun setupAutoSave() {
        syncAutoSaveUI()
        binding.switchAutoSave.setOnCheckedChangeListener { _, checked ->
            prefs.autoSaveEnabled = checked
            syncAutoSaveUI()
        }
        binding.rowAutoSave.setOnClickListener { binding.switchAutoSave.isChecked = !binding.switchAutoSave.isChecked }

        binding.switchAutoSaveOnPause.setOnCheckedChangeListener { _, checked -> prefs.autoSaveOnPause = checked }
        binding.rowAutoSaveOnPause.setOnClickListener {
            if (!prefs.autoSaveEnabled) return@setOnClickListener
            binding.switchAutoSaveOnPause.isChecked = !binding.switchAutoSaveOnPause.isChecked
        }

        binding.switchAutoSaveOnTabSwitch.setOnCheckedChangeListener { _, checked -> prefs.autoSaveOnTabSwitch = checked }
        binding.rowAutoSaveOnTabSwitch.setOnClickListener {
            if (!prefs.autoSaveEnabled) return@setOnClickListener
            binding.switchAutoSaveOnTabSwitch.isChecked = !binding.switchAutoSaveOnTabSwitch.isChecked
        }

        binding.switchAutoSaveOnEdit.setOnCheckedChangeListener { _, checked -> prefs.autoSaveOnEdit = checked }
        binding.rowAutoSaveOnEdit.setOnClickListener {
            if (!prefs.autoSaveEnabled) return@setOnClickListener
            binding.switchAutoSaveOnEdit.isChecked = !binding.switchAutoSaveOnEdit.isChecked
        }

        binding.rowAutoSaveInterval.setOnClickListener {
            if (!prefs.autoSaveEnabled) return@setOnClickListener
            showAutoSaveIntervalDialog()
        }
    }

    private fun syncAutoSaveUI() {
        binding.switchAutoSave.isChecked = prefs.autoSaveEnabled
        val enabled = prefs.autoSaveEnabled
        val alpha = if (enabled) 1f else 0.4f

        binding.switchAutoSaveOnPause.isChecked = prefs.autoSaveOnPause
        binding.switchAutoSaveOnTabSwitch.isChecked = prefs.autoSaveOnTabSwitch
        binding.switchAutoSaveOnEdit.isChecked = prefs.autoSaveOnEdit
        updateAutoSaveIntervalLabel()

        binding.rowAutoSaveOnPause.isEnabled = enabled
        binding.switchAutoSaveOnPause.isEnabled = enabled
        binding.rowAutoSaveOnPause.alpha = alpha

        binding.rowAutoSaveOnTabSwitch.isEnabled = enabled
        binding.switchAutoSaveOnTabSwitch.isEnabled = enabled
        binding.rowAutoSaveOnTabSwitch.alpha = alpha

        binding.rowAutoSaveOnEdit.isEnabled = enabled
        binding.switchAutoSaveOnEdit.isEnabled = enabled
        binding.rowAutoSaveOnEdit.alpha = alpha

        binding.rowAutoSaveInterval.isEnabled = enabled
        binding.tvAutoSaveIntervalValue.isEnabled = enabled
        binding.rowAutoSaveInterval.alpha = alpha
    }

    private fun updateAutoSaveIntervalLabel() {
        binding.tvAutoSaveIntervalValue.text = autoSaveIntervalName(prefs.autoSaveIntervalSec)
    }

    private fun autoSaveIntervalName(seconds: Int): String = getString(
        when (seconds) {
            0 -> R.string.auto_save_interval_off
            15 -> R.string.auto_save_interval_15
            30 -> R.string.auto_save_interval_30
            60 -> R.string.auto_save_interval_60
            300 -> R.string.auto_save_interval_300
            600 -> R.string.auto_save_interval_600
            else -> R.string.auto_save_interval_off
        }
    )

    private fun showAutoSaveIntervalDialog() {
        val values = intArrayOf(0, 15, 30, 60, 300, 600)
        val names = values.map { autoSaveIntervalName(it) }.toTypedArray()
        val current = values.indexOf(prefs.autoSaveIntervalSec).coerceAtLeast(0)
        showChoiceDialog(R.string.auto_save_interval_title, names, current) { which ->
            prefs.autoSaveIntervalSec = values[which]
            updateAutoSaveIntervalLabel()
        }
    }

    private fun setupUtf8Bom() {
        binding.switchUtf8Bom.isChecked = prefs.utf8Bom
        binding.switchUtf8Bom.setOnCheckedChangeListener { _, checked -> prefs.utf8Bom = checked }
        binding.rowUtf8Bom.setOnClickListener {
            binding.switchUtf8Bom.isChecked = !binding.switchUtf8Bom.isChecked
        }
    }

    private fun setupReplaceRules() {
        binding.tvReplaceRules.visibility = if (rulesExpanded) View.VISIBLE else View.GONE
        binding.ivRulesExpand.rotation = if (rulesExpanded) 180f else 0f
        binding.rowReplaceRules.setOnClickListener {
            rulesExpanded = !rulesExpanded
            binding.tvReplaceRules.visibility = if (rulesExpanded) View.VISIBLE else View.GONE
            binding.ivRulesExpand.animate().rotation(if (rulesExpanded) 180f else 0f).setDuration(180).start()
        }
    }
}