package com.sunwenqing.plainpad

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sunwenqing.plainpad.databinding.ActivitySettingsBinding
import com.sunwenqing.plainpad.databinding.DialogChoiceBinding
import com.sunwenqing.plainpad.databinding.DialogChoiceItemBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefs: Prefs

    private var suppressStatusListener = false

    private var editingExpanded = true
    private var saveExpanded = true
    private var appearanceExpanded = true
    private var tabsExpanded = true

    private var rulesExpanded = false

    // ---------------- 搜索入口 ----------------

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

        // ★ 主题色（必须在 super.onCreate 之前）
        ThemeColorHelper.apply(this, prefs.themeColor)

        AppCompatDelegate.setDefaultNightMode(prefs.themeMode)
        super.onCreate(savedInstanceState)

        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

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
        setupStartupMode()
        setupPlusBehavior()
        setupStatusBarItems()
        setupAutoSave()
        setupReplaceRules()
    }

    override fun onResume() {
        super.onResume()
        updateEditorThemeLabel()
        updateEditorFontLabel()
        updatePlusHint(prefs.plusReuseEmpty)
        syncStatusBarCheckboxes()
        syncAutoSaveUI()
        updateThemeColorLabel()
    }

    // ---------------- 板块折叠 ----------------

    private fun setupSections() {
        bindSection(
            header = binding.headerEditing,
            content = binding.contentEditing,
            arrow = binding.ivExpandEditing,
            isExpanded = { editingExpanded },
            setExpanded = { editingExpanded = it }
        )
        bindSection(
            header = binding.headerSave,
            content = binding.contentSave,
            arrow = binding.ivExpandSave,
            isExpanded = { saveExpanded },
            setExpanded = { saveExpanded = it }
        )
        bindSection(
            header = binding.headerAppearance,
            content = binding.contentAppearance,
            arrow = binding.ivExpandAppearance,
            isExpanded = { appearanceExpanded },
            setExpanded = { appearanceExpanded = it }
        )
        bindSection(
            header = binding.headerTabs,
            content = binding.contentTabs,
            arrow = binding.ivExpandTabs,
            isExpanded = { tabsExpanded },
            setExpanded = { tabsExpanded = it }
        )

        applySectionState(binding.contentEditing, binding.ivExpandEditing, editingExpanded)
        applySectionState(binding.contentSave, binding.ivExpandSave, saveExpanded)
        applySectionState(binding.contentAppearance, binding.ivExpandAppearance, appearanceExpanded)
        applySectionState(binding.contentTabs, binding.ivExpandTabs, tabsExpanded)
    }

    private fun bindSection(
        header: View,
        content: View,
        arrow: View,
        isExpanded: () -> Boolean,
        setExpanded: (Boolean) -> Unit
    ) {
        header.setOnClickListener {
            val newState = !isExpanded()
            setExpanded(newState)
            applySectionState(content, arrow, newState)
        }
    }

    private fun applySectionState(content: View, arrow: View, expanded: Boolean) {
        content.visibility = if (expanded) View.VISIBLE else View.GONE
        arrow.animate()
            .rotation(if (expanded) 180f else 0f)
            .setDuration(180)
            .start()
    }

    // ---------------- 右侧滚动条 ----------------

    private fun setupScrollBar() {
        binding.settingsScrollBar.attachScrollView(binding.settingsScroll)
    }

    // ---------------- 搜索定位 ----------------

    private fun locateSetting(key: String) {
        editingExpanded = true
        saveExpanded = true
        appearanceExpanded = true
        tabsExpanded = true
        applySectionState(binding.contentEditing, binding.ivExpandEditing, true)
        applySectionState(binding.contentSave, binding.ivExpandSave, true)
        applySectionState(binding.contentAppearance, binding.ivExpandAppearance, true)
        applySectionState(binding.contentTabs, binding.ivExpandTabs, true)

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
        val highlight = android.graphics.drawable.GradientDrawable().apply {
            setColor(
                MaterialColors.getColor(
                    view, com.google.android.material.R.attr.colorPrimaryContainer
                )
            )
            cornerRadius = 10 * density
        }
        view.background = highlight
        view.postDelayed({ view.background = original }, 1500)
    }

    // ---------------- 字体大小 ----------------

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

    // ---------------- 开关 ----------------

    private fun setupSwitches() {
        binding.switchWrap.isChecked = prefs.wrap
        binding.switchLineNumbers.isChecked = prefs.showLineNumbers
        binding.switchShowSpecialChars.isChecked = prefs.showSpecialChars

        binding.switchWrap.setOnCheckedChangeListener { _, checked ->
            prefs.wrap = checked
        }
        binding.switchLineNumbers.setOnCheckedChangeListener { _, checked ->
            prefs.showLineNumbers = checked
        }
        binding.switchShowSpecialChars.setOnCheckedChangeListener { _, checked ->
            prefs.showSpecialChars = checked
        }

        binding.rowWrap.setOnClickListener {
            binding.switchWrap.isChecked = !binding.switchWrap.isChecked
        }
        binding.rowLineNumbers.setOnClickListener {
            binding.switchLineNumbers.isChecked = !binding.switchLineNumbers.isChecked
        }
        binding.rowShowSpecialChars.setOnClickListener {
            binding.switchShowSpecialChars.isChecked =
                !binding.switchShowSpecialChars.isChecked
        }
    }

    // ---------------- 应用主题（浅色/深色） ----------------

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

    // ---------------- 主题色 ----------------

    private fun setupThemeColor() {
        updateThemeColorLabel()
        binding.rowThemeColor.setOnClickListener { showThemeColorDialog() }
    }

    private fun updateThemeColorLabel() {
        binding.tvThemeColorValue.text = themeColorName(prefs.themeColor)
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
        val ids = arrayOf(
            "system", "purple", "blue", "indigo", "teal",
            "green", "orange", "red", "pink"
        )
        val names = ids.map { themeColorName(it) }.toTypedArray()
        val current = ids.indexOf(prefs.themeColor).coerceAtLeast(0)

        val dynamicAvailable = ThemeColorHelper.isDynamicColorAvailable(this)

        showChoiceDialog(R.string.theme_color_title, names, current) { which ->
            val picked = ids[which]
            if (picked == "system" && !dynamicAvailable) {
                Toast.makeText(
                    this,
                    getString(R.string.theme_color_system_unavailable),
                    Toast.LENGTH_SHORT
                ).show()
                return@showChoiceDialog
            }
            if (picked != prefs.themeColor) {
                prefs.themeColor = picked
                updateThemeColorLabel()
                recreate()
            }
        }
    }

    // ---------------- 编辑区主题 ----------------

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
        val current = EditorThemes.ALL.indexOfFirst { it.id == prefs.editorThemeId }
            .coerceAtLeast(0)

        showChoiceDialog(R.string.settings_editor_theme, names, current) { which ->
            prefs.editorThemeId = EditorThemes.ALL[which].id
            updateEditorThemeLabel()
        }
    }

    // ---------------- 编辑区字体 ----------------

    private fun setupEditorFont() {
        updateEditorFontLabel()
        binding.rowEditorFont.setOnClickListener { showEditorFontDialog() }
    }

    private fun updateEditorFontLabel() {
        binding.tvEditorFontValue.text = getString(
            EditorThemes.fontNameRes(prefs.editorFontFamily)
        )
    }

    private fun showEditorFontDialog() {
        val names = EditorThemes.FONTS.map { getString(it.second) }.toTypedArray()
        val current = EditorThemes.FONTS.indexOfFirst {
            it.first == prefs.editorFontFamily
        }.coerceAtLeast(0)

        showChoiceDialog(R.string.settings_editor_font, names, current) { which ->
            prefs.editorFontFamily = EditorThemes.FONTS[which].first
            updateEditorFontLabel()
        }
    }

    // ---------------- 自定义单选对话框 ----------------

    private fun showChoiceDialog(
        titleRes: Int,
        names: Array<String>,
        selectedIndex: Int,
        onPick: (Int) -> Unit
    ) {
        val b = DialogChoiceBinding.inflate(layoutInflater)
        b.tvDialogTitle.setText(titleRes)

        val dialog = MaterialAlertDialogBuilder(this)
            .setView(b.root)
            .create()

        names.forEachIndexed { i, name ->
            val item = DialogChoiceItemBinding.inflate(
                layoutInflater, b.choiceContainer, false
            )
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

    // ---------------- 启动模式 ----------------

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

    // ---------------- 加号行为 ----------------

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
        binding.tvPlusHint.text = getString(
            if (reuse) R.string.plus_reuse_empty else R.string.plus_always_new
        )
    }

    // ---------------- 状态栏显示项 ----------------

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
        val current = prefs.statusBarItems
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toMutableList()

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
        val current = prefs.statusBarItems
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        statusBoxes().forEach { (cb, id) ->
            cb.isChecked = current.contains(id)
        }
        suppressStatusListener = false
    }

    // ---------------- 自动保存 ----------------

    private fun setupAutoSave() {
        syncAutoSaveUI()

        binding.switchAutoSave.setOnCheckedChangeListener { _, checked ->
            prefs.autoSaveEnabled = checked
            syncAutoSaveUI()
        }
        binding.rowAutoSave.setOnClickListener {
            binding.switchAutoSave.isChecked = !binding.switchAutoSave.isChecked
        }

        binding.switchAutoSaveOnPause.setOnCheckedChangeListener { _, checked ->
            prefs.autoSaveOnPause = checked
        }
        binding.rowAutoSaveOnPause.setOnClickListener {
            if (!prefs.autoSaveEnabled) return@setOnClickListener
            binding.switchAutoSaveOnPause.isChecked = !binding.switchAutoSaveOnPause.isChecked
        }

        binding.switchAutoSaveOnTabSwitch.setOnCheckedChangeListener { _, checked ->
            prefs.autoSaveOnTabSwitch = checked
        }
        binding.rowAutoSaveOnTabSwitch.setOnClickListener {
            if (!prefs.autoSaveEnabled) return@setOnClickListener
            binding.switchAutoSaveOnTabSwitch.isChecked =
                !binding.switchAutoSaveOnTabSwitch.isChecked
        }

        binding.switchAutoSaveOnEdit.setOnCheckedChangeListener { _, checked ->
            prefs.autoSaveOnEdit = checked
        }
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

    // ---------------- 替换规则折叠 ----------------

    private fun setupReplaceRules() {
        binding.tvReplaceRules.visibility = if (rulesExpanded) View.VISIBLE else View.GONE
        binding.ivRulesExpand.rotation = if (rulesExpanded) 180f else 0f

        binding.rowReplaceRules.setOnClickListener {
            rulesExpanded = !rulesExpanded
            binding.tvReplaceRules.visibility =
                if (rulesExpanded) View.VISIBLE else View.GONE
            binding.ivRulesExpand.animate()
                .rotation(if (rulesExpanded) 180f else 0f)
                .setDuration(180)
                .start()
        }
    }
}