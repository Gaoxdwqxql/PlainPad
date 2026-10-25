package com.sunwenqing.plainpad

import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.ListPopupWindow
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.sunwenqing.plainpad.databinding.ActivityMainBinding
import com.sunwenqing.plainpad.databinding.DialogChoiceItemBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: Prefs
    private lateinit var undoManager: UndoManager
    private lateinit var zoomHandler: ZoomGestureHandler

    private val documents = mutableListOf<OpenDocument>()
    private var currentIndex = -1

    private var suppressWatcher = false
    private var pendingAction: (() -> Unit)? = null
    private var pendingEncoding: String? = null
    private var isImmersive = false
    private var untitledCounter = 0
    private var uiReady = false

    private var currentThemeColor = ""
    private var skipIconSwitchOnce = false
    private var currentEol = "LF"

    private val LARGE_FILE_THRESHOLD = 3_000_000
    private var isLargeFileMode = false

    private var lastAppliedFontSize = -1
    private var lastAppliedFontFamily = ""
    private var lastAppliedEditorTheme = ""
    private var lastAppliedWrap: Boolean? = null

    private val autoSaveHandler = Handler(Looper.getMainLooper())
    private var autoSaveRunnable: Runnable? = null

    private val editAutoSaveHandler = Handler(Looper.getMainLooper())
    private var editAutoSaveRunnable: Runnable? = null
    private val EDIT_AUTO_SAVE_DELAY_MS = 2000L

    private val specialCharHandler = Handler(Looper.getMainLooper())
    private var specialCharRunnable: Runnable? = null
    private val SPECIAL_CHAR_REFRESH_DELAY_MS = 150L

    private val statusUpdateHandler = Handler(Looper.getMainLooper())
    private var statusUpdateRunnable: Runnable? = null
    private val STATUS_UPDATE_DELAY_MS = 400L

    private val openLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (!uiReady) return@registerForActivityResult
            if (uri == null) return@registerForActivityResult
            EditorFileIO.takePersistable(contentResolver, uri)
            openDocumentIntoNewTab(uri)
        }

    private val saveAsLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
            if (uri == null) {
                pendingAction = null
                pendingEncoding = null
                return@registerForActivityResult
            }
            if (!uiReady) return@registerForActivityResult
            EditorFileIO.takePersistable(contentResolver, uri)
            saveCurrentToUri(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        prefs = Prefs(this)
        currentThemeColor = prefs.themeColor
        ThemeColorHelper.apply(this, currentThemeColor)
        AppCompatDelegate.setDefaultNightMode(prefs.themeMode)
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        if (!prefs.onboardingDone && savedInstanceState == null) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // ★ 初始化顶部和底部纯色填充
        binding.statusBarFill.setStatusBarHeight()
        binding.navigationBarFill.setNavigationBarHeight()

        ThemeColorHelper.applySystemBars(this)

        undoManager = UndoManager(binding.editor)
        zoomHandler = ZoomGestureHandler(
            initialFontSizeProvider = { prefs.fontSize.toFloat() },
            onPreview = { EditorStyler.applyFontSize(binding, it) },
            onCommit = { commitFontSize(it) }
        )

        setupEditor()
        setupLineNumbers()
        setupScrollBar()
        setupBackHandler()
        setupZoomGesture()
        setupImmersiveExitButton()
        setupTopBarButtons()

        binding.tvLineNumbers.visibility = if (prefs.showLineNumbers) View.VISIBLE else View.GONE

        applyWrap(prefs.wrap)
        lastAppliedWrap = prefs.wrap
        EditorStyler.applyAll(binding, prefs)
        lastAppliedFontSize = prefs.fontSize
        lastAppliedFontFamily = prefs.editorFontFamily
        lastAppliedEditorTheme = prefs.editorThemeId

        if (savedInstanceState != null) {
            restoreState(savedInstanceState)
            loadMissingTextsAsync()
            if (intent != null && intent.action != Intent.ACTION_MAIN) {
                handleIncomingIntent(intent)
            }
        } else {
            initializeFromStartupMode()
            handleIncomingIntent(intent)
        }

        if (documents.isEmpty()) {
            newTabInternal(isFirst = true)
        }

        refreshTabBar()
        refreshCurrentView()
        scheduleAutoSave()

        uiReady = true
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (uiReady) {
            handleIncomingIntent(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        if (!uiReady) return

        if (prefs.themeColor != currentThemeColor) {
            saveCurrentToDoc()
            skipIconSwitchOnce = true
            recreate()
            return
        }

        applyAppearanceIfChanged()

        binding.tvLineNumbers.visibility =
            if (prefs.showLineNumbers && !isImmersive && !isLargeFileMode) View.VISIBLE else View.GONE

        if (!isLargeFileMode) {
            triggerStatusUpdate()
            scheduleSpecialCharRefresh()
        }
        updateAutoSaveStatus()
        scheduleAutoSave()
    }

    private fun applyAppearanceIfChanged() {
        val themeChanged = prefs.editorThemeId != lastAppliedEditorTheme
        val fontChanged = prefs.fontSize != lastAppliedFontSize ||
                prefs.editorFontFamily != lastAppliedFontFamily

        if (themeChanged || fontChanged) {
            if (!isLargeFileMode) {
                EditorStyler.applyAll(binding, prefs)
            } else {
                val theme = EditorThemes.byId(prefs.editorThemeId)
                EditorThemes.apply(
                    theme,
                    this,
                    binding.editArea,
                    binding.editor,
                    binding.tvLineNumbers
                )
                val tf = FontHelper.getTypeface(prefs)
                val textColor = binding.editor.currentTextColor
                val textSizePx = prefs.fontSize * resources.displayMetrics.scaledDensity
                binding.largeTextView.setTextAppearance(tf, textSizePx, textColor)
                binding.largeTextView.setBackgroundColor(theme.bg ?: resolveThemeColor(com.google.android.material.R.attr.colorSurface))
            }
        }

        lastAppliedFontSize = prefs.fontSize
        lastAppliedFontFamily = prefs.editorFontFamily
        lastAppliedEditorTheme = prefs.editorThemeId

        if (isLargeFileMode) {
            applyWrap(false)
        } else if (prefs.wrap != lastAppliedWrap) {
            applyWrap(prefs.wrap)
        }
        lastAppliedWrap = prefs.wrap
    }

    override fun onPause() {
        if (uiReady) {
            saveCurrentToDoc()
            if (prefs.autoSaveEnabled && prefs.autoSaveOnPause) {
                autoSaveCurrent()
            }
            if (documents.isNotEmpty()) {
                prefs.saveTabs(documents)
            }
        }
        cancelAutoSave()
        cancelEditAutoSave()
        cancelSpecialCharRefresh()
        cancelStatusUpdate()
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (!uiReady) return
        saveCurrentToDoc()
        InstanceStateStore.save(outState, documents, currentIndex, untitledCounter)
    }

    override fun onStop() {
        super.onStop()
        if (uiReady && !skipIconSwitchOnce && !isChangingConfigurations) {
            IconSwitcher.applyIfNeeded(this, prefs.themeColor)
        }
        skipIconSwitchOnce = false
    }

    override fun onDestroy() {
        cancelAutoSave()
        cancelEditAutoSave()
        cancelSpecialCharRefresh()
        cancelStatusUpdate()
        if (uiReady) {
            undoManager.release()
        }
        super.onDestroy()
    }

    private fun setupEditor() {
        EditorStyler.applyFontSize(binding, prefs.fontSize.toFloat())
        binding.editor.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (!suppressWatcher && !isLargeFileMode) {
                    val doc = currentDoc() ?: return
                    if (doc.isReadOnly) {
                        showTip(getString(R.string.read_only_cannot_edit))
                        suppressWatcher = true
                        binding.editor.setText(doc.text)
                        binding.editor.setSelection(doc.selStart.coerceIn(0, doc.text.length))
                        suppressWatcher = false
                        return
                    }

                    doc.text = s?.toString().orEmpty()
                    if (!doc.isDirty) {
                        doc.isDirty = true
                        refreshTabBar()
                    }

                    undoManager.scheduleSnapshot()
                    scheduleEditAutoSave()
                    if (prefs.showSpecialChars) {
                        scheduleSpecialCharRefresh()
                    }
                    triggerStatusUpdate()
                }
                updateAutoSaveStatus()
            }
        })
    }

    private fun triggerStatusUpdate() {
        cancelStatusUpdate()
        val r = Runnable {
            updateEolLabel()
            if (isLargeFileMode) return@Runnable
            updateStatus()
            updateLineNumbers()
        }
        statusUpdateRunnable = r
        statusUpdateHandler.postDelayed(r, STATUS_UPDATE_DELAY_MS)
    }

    private fun cancelStatusUpdate() {
        statusUpdateRunnable?.let { statusUpdateHandler.removeCallbacks(it) }
        statusUpdateRunnable = null
    }

    private fun setupLineNumbers() {
        binding.tvLineNumbers.attach(binding.editor)
    }

    private fun setupScrollBar() {
        binding.fastScrollBar.attach(binding.editor)
    }

    private fun setupBackHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isImmersive) {
                    applyImmersive(false)
                } else {
                    finish()
                }
            }
        })
    }

    private fun setupZoomGesture() {
        binding.editor.setOnTouchListener { v, event ->
            if (isLargeFileMode) return@setOnTouchListener false
            zoomHandler.onTouch(v, event)
        }
    }

    private fun setupImmersiveExitButton() {
        binding.btnExitImmersive.setOnClickListener { applyImmersive(false) }
    }

    private fun setupTopBarButtons() {
        binding.btnUndo.setOnClickListener { undo() }
        binding.btnRedo.setOnClickListener { redo() }
        binding.btnFind.setOnClickListener { showFind() }
        binding.btnSave.setOnClickListener { showSaveMenu() }
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        binding.btnMore.setOnClickListener { showMoreMenu() }
        binding.tvStatus.setOnClickListener {
            if (isLargeFileMode) showTip(getString(R.string.large_file_stats_disabled)) else showStats()
        }
        binding.btnInsertSpecial.setOnClickListener {
            if (isLargeFileMode || currentDoc()?.isReadOnly == true) {
                showTip(getString(R.string.large_file_feature_disabled))
            } else {
                showSpecialCharPanel()
            }
        }
        binding.tvEol.setOnClickListener { showEolMenu() }
    }

    private fun showLoading() {
        binding.tvLoadingText.text = getString(R.string.loading_large_file)
        binding.loadingOverlay.alpha = 0f
        binding.loadingOverlay.visibility = View.VISIBLE
        binding.loadingOverlay.animate().alpha(1f).setDuration(200).start()
    }

    private fun hideLoading() {
        if (binding.loadingOverlay.visibility == View.VISIBLE) {
            binding.loadingOverlay.animate().alpha(0f).setDuration(200)
                .withEndAction { binding.loadingOverlay.visibility = View.GONE }
                .start()
        }
    }

    private fun showCustomMenu(anchor: View, items: List<Pair<String, () -> Unit>>) {
        val popup = ListPopupWindow(this)
        popup.anchorView = anchor
        popup.isModal = true
        val density = resources.displayMetrics.density
        popup.width = (220 * density).toInt()
        val bg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 14f * density
            setColor(resolveThemeColor(com.google.android.material.R.attr.colorSurfaceContainerHigh))
            setStroke((1 * density).toInt(), resolveThemeColor(com.google.android.material.R.attr.colorOutlineVariant))
        }
        popup.setBackgroundDrawable(InsetDrawable(bg, (4 * density).toInt(), (4 * density).toInt(), (4 * density).toInt(), (4 * density).toInt()))
        popup.setAdapter(object : BaseAdapter() {
            override fun getCount() = items.size
            override fun getItem(position: Int) = items[position]
            override fun getItemId(position: Int) = position.toLong()
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val b = if (convertView != null) DialogChoiceItemBinding.bind(convertView)
                else DialogChoiceItemBinding.inflate(layoutInflater, parent, false)
                b.tvOptionText.text = items[position].first
                b.ivCheck.visibility = View.GONE
                return b.root
            }
        })
        popup.setOnItemClickListener { _, _, position, _ ->
            popup.dismiss()
            items[position].second.invoke()
        }
        popup.show()
    }

    private fun resolveThemeColor(attr: Int): Int {
        val tv = android.util.TypedValue()
        return if (theme.resolveAttribute(attr, tv, true)) {
            if (tv.resourceId != 0) androidx.core.content.ContextCompat.getColor(this, tv.resourceId) else tv.data
        } else 0xFFEEEEEE.toInt()
    }

    private fun updateEolLabel() {
        val text = if (isLargeFileMode) currentDoc()?.text else binding.editor.text
        if (text == null) return
        currentEol = EditorCommands.detectEol(text)
        binding.tvEol.text = when (currentEol) {
            "CRLF" -> "CRLF"
            "LF" -> "LF"
            else -> "Mixed"
        }
    }

    private fun showEolMenu() {
        if (isLargeFileMode || currentDoc()?.isReadOnly == true) {
            showTip(getString(R.string.cannot_convert_eol))
            return
        }
        val items = listOf(
            getString(R.string.convert_to_crlf) to { convertEol("\r\n") },
            getString(R.string.convert_to_lf) to { convertEol("\n") }
        )
        showCustomMenu(binding.tvEol, items)
    }

    private fun convertEol(targetEol: String) {
        if (isLargeFileMode) return
        val doc = currentDoc() ?: return
        if (doc.isReadOnly) {
            showTip(getString(R.string.read_only_cannot_edit))
            return
        }

        val editor = binding.editor
        val text = editor.text.toString()

        if (text.isEmpty()) {
            showTip(getString(R.string.eol_convert_empty))
            return
        }

        if (!text.contains('\n')) {
            showTip(getString(R.string.eol_convert_no_newline))
            return
        }

        val targetLabel = if (targetEol == "\r\n") "CRLF" else "LF"
        if (currentEol == targetLabel) {
            showTip(getString(R.string.already_in_format, targetLabel))
            return
        }

        val normalized = text.replace("\r\n", "\n")
        val newText = if (targetEol == "\r\n") normalized.replace("\n", "\r\n") else normalized

        if (newText == text) {
            showTip(getString(R.string.already_in_format, targetLabel))
            return
        }

        val selStart = editor.selectionStart

        suppressWatcher = true
        editor.setText(newText)
        suppressWatcher = false

        doc.text = newText
        doc.isDirty = true
        refreshTabBar()

        undoManager.reset(newText)

        val newSel = selStart.coerceIn(0, newText.length)
        editor.setSelection(newSel)

        updateEolLabel()
        triggerStatusUpdate()
        showTip(getString(R.string.converted_to_format, targetLabel))
    }

    private fun showSaveMenu() {
        showCustomMenu(
            binding.btnSave,
            listOf(
                getString(R.string.menu_save_all) to { saveAll() },
                getString(R.string.menu_save_current) to { saveCurrent() },
                getString(R.string.menu_save_as) to { chooseEncodingThenSaveAs() }
            )
        )
    }

    private fun saveAll() {
        saveCurrentToDoc()
        val targets = documents.filter { it.uri != null && it.isDirty && !it.isReadOnly }
        if (targets.isEmpty()) {
            showTip(getString(R.string.save_all_nothing))
            return
        }
        lifecycleScope.launch {
            var success = 0
            var failed = 0
            for (doc in targets) {
                try {
                    withContext(Dispatchers.IO) {
                        EditorFileIO.save(contentResolver, doc.uri!!, doc.text, doc.encoding)
                    }
                    doc.isDirty = false
                    success++
                } catch (_: Exception) { failed++ }
            }
            if (success > 0) prefs.lastSaveTime = System.currentTimeMillis()
            refreshTabBar()
            updateAutoSaveStatus()
            if (failed == 0) showTip(getString(R.string.save_all_success, success))
            else showTip(getString(R.string.save_all_partial, success, failed))
        }
    }

    private fun showMoreMenu() {
        showCustomMenu(
            binding.btnMore,
            listOf(
                getString(R.string.menu_new) to { newTab() },
                getString(R.string.menu_open) to { openLauncher.launch(arrayOf("text/plain", "text/*")) },
                getString(R.string.menu_recent) to { showRecent() },
                getString(R.string.menu_close_tab) to { requestCloseTab(currentIndex) },
                getString(R.string.menu_replace) to { showReplace() },
                getString(R.string.menu_goto_line) to { showGotoLine() },
                getString(R.string.menu_immersive) to { applyImmersive(true) },
                getString(R.string.menu_default) to { openDefaultAppsSettings() },
                getString(R.string.menu_help) to { startActivity(Intent(this, HelpActivity::class.java)) },
                getString(R.string.menu_about) to { startActivity(Intent(this, AboutActivity::class.java)) }
            )
        )
    }

    private fun commitFontSize(sizeSp: Int) {
        if (isLargeFileMode) {
            showTip(getString(R.string.large_file_cannot_resize))
            return
        }
        val finalSize = sizeSp.coerceIn(10, 40)
        if (finalSize != prefs.fontSize) prefs.fontSize = finalSize
        EditorStyler.applyFontSize(binding, finalSize.toFloat())
        lastAppliedFontSize = finalSize
        if (prefs.showSpecialChars && !isLargeFileMode) {
            scheduleSpecialCharRefresh()
        }
    }

    private fun showTip(msg: String) {
        val anchor = if (isImmersive) binding.btnExitImmersive else binding.statusBar
        Snackbar.make(binding.root, msg, Snackbar.LENGTH_SHORT).setAnchorView(anchor).show()
    }

    private fun scheduleAutoSave() {
        cancelAutoSave()
        if (!prefs.autoSaveEnabled || prefs.autoSaveIntervalSec <= 0) return
        val r = object : Runnable {
            override fun run() {
                if (prefs.autoSaveEnabled && prefs.autoSaveIntervalSec > 0) {
                    autoSaveCurrent()
                    autoSaveHandler.postDelayed(this, prefs.autoSaveIntervalSec * 1000L)
                }
            }
        }
        autoSaveRunnable = r
        autoSaveHandler.postDelayed(r, prefs.autoSaveIntervalSec * 1000L)
    }

    private fun cancelAutoSave() {
        autoSaveRunnable?.let { autoSaveHandler.removeCallbacks(it) }
        autoSaveRunnable = null
    }

    private fun scheduleEditAutoSave() {
        if (!prefs.autoSaveEnabled || !prefs.autoSaveOnEdit || isLargeFileMode) return
        val doc = currentDoc() ?: return
        if (doc.uri == null || !doc.isDirty || doc.isReadOnly) return
        editAutoSaveRunnable?.let { editAutoSaveHandler.removeCallbacks(it) }
        val r = Runnable { autoSaveCurrent() }
        editAutoSaveRunnable = r
        editAutoSaveHandler.postDelayed(r, EDIT_AUTO_SAVE_DELAY_MS)
    }

    private fun cancelEditAutoSave() {
        editAutoSaveRunnable?.let { editAutoSaveHandler.removeCallbacks(it) }
        editAutoSaveRunnable = null
    }

    private fun autoSaveCurrent() {
        if (!prefs.autoSaveEnabled || isLargeFileMode) return
        val doc = currentDoc() ?: return
        if (doc.uri == null || !doc.isDirty || doc.isReadOnly) return
        val content = binding.editor.text?.toString().orEmpty()
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    EditorFileIO.save(contentResolver, doc.uri!!, content, doc.encoding)
                }
                doc.text = content
                doc.isDirty = false
                prefs.lastSaveTime = System.currentTimeMillis()
                refreshTabBar()
                updateAutoSaveStatus()
            } catch (_: Exception) {}
        }
    }

    private fun updateAutoSaveStatus() {
        val t = prefs.lastSaveTime
        if (t > 0) {
            val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
            binding.tvAutoSave.text = getString(R.string.auto_save_last_time, fmt.format(Date(t)))
        } else {
            binding.tvAutoSave.text = getString(R.string.auto_save_never)
        }
        binding.tvAutoSave.visibility = View.VISIBLE
    }

    private fun scheduleSpecialCharRefresh() {
        if (isLargeFileMode) return
        cancelSpecialCharRefresh()
        val r = Runnable { SpecialCharRenderer.apply(binding.editor, prefs.showSpecialChars) }
        specialCharRunnable = r
        specialCharHandler.postDelayed(r, SPECIAL_CHAR_REFRESH_DELAY_MS)
    }

    private fun cancelSpecialCharRefresh() {
        specialCharRunnable?.let { specialCharHandler.removeCallbacks(it) }
        specialCharRunnable = null
    }

    private fun applyImmersive(on: Boolean) {
        if (on == isImmersive) return
        isImmersive = on
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
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            binding.statusBarFill.visibility = View.VISIBLE
            binding.navigationBarFill.visibility = View.VISIBLE
            binding.topBar.visibility = View.VISIBLE
            binding.tabScroll.visibility = View.VISIBLE
            binding.statusBar.visibility = View.VISIBLE
            binding.tvLineNumbers.visibility =
                if (prefs.showLineNumbers && !isLargeFileMode) View.VISIBLE else View.GONE
            binding.btnExitImmersive.visibility = View.GONE
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowInsetsControllerCompat(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
            ThemeColorHelper.applySystemBars(this)
        }
    }

    private fun initializeFromStartupMode() {
        when (prefs.startupMode) {
            0 -> {
                val saved = prefs.loadTabs()
                if (saved.isNotEmpty()) {
                    documents.clear()
                    documents.addAll(saved)
                    currentIndex = 0
                    loadDocIntoEditor(documents[0])
                    loadMissingTextsAsync()
                } else {
                    newTabInternal(isFirst = true)
                }
            }
            1 -> {
                prefs.clearSavedTabs()
                newTabInternal(isFirst = true)
            }
            2 -> {
                newTabInternal(isFirst = true)
                val saved = prefs.loadTabs()
                for (d in saved) documents.add(d)
                if (documents.isNotEmpty()) {
                    val newIdx = documents.indexOfFirst { it.uri == null && !it.isDirty && it.text.isEmpty() }
                    if (newIdx >= 0 && newIdx != documents.size - 1) {
                        val nd = documents.removeAt(newIdx)
                        documents.add(nd)
                    }
                    currentIndex = documents.size - 1
                    loadDocIntoEditor(documents[currentIndex])
                }
                loadMissingTextsAsync()
            }
        }
    }

    private fun loadMissingTextsAsync() {
        documents.forEachIndexed { index, doc ->
            if (doc.text.isEmpty() && doc.uri != null && !doc.isDirty) {
                lifecycleScope.launch {
                    try {
                        val result = withContext(Dispatchers.IO) { EditorFileIO.load(contentResolver, doc.uri!!) }
                        doc.text = result.text
                        doc.encoding = result.encoding
                        doc.isReadOnly = result.isTruncated
                        if (index == currentIndex) {
                            loadDocIntoEditor(doc)
                        }
                        refreshTabBar()
                    } catch (_: Exception) {}
                }
            }
        }
    }

    private fun currentDoc(): OpenDocument? = documents.getOrNull(currentIndex)

    private fun newTab() {
        if (prefs.plusReuseEmpty) {
            val current = currentDoc()
            val alreadyOnEmpty = current != null && current.uri == null && !current.isDirty && current.text.isEmpty()
            if (!alreadyOnEmpty) {
                val emptyIdx = documents.indexOfFirst { it.uri == null && !it.isDirty && it.text.isEmpty() }
                if (emptyIdx >= 0) {
                    switchToTab(emptyIdx)
                    return
                }
            }
        }
        newTabInternal(isFirst = false)
    }

    private fun newTabInternal(isFirst: Boolean) {
        untitledCounter++
        val name = if (isFirst && documents.isEmpty()) getString(R.string.untitled) else getString(R.string.untitled_n, untitledCounter)
        val doc = OpenDocument(displayName = name)
        saveCurrentToDoc()
        documents.add(doc)
        currentIndex = documents.size - 1
        loadDocIntoEditor(doc)
        refreshTabBar()
        refreshCurrentView()
    }

    private fun openDocumentIntoNewTab(uri: Uri) {
        val existing = documents.indexOfFirst { it.uri == uri }
        if (existing >= 0) {
            switchToTab(existing)
            return
        }
        lifecycleScope.launch {
            try {
                showLoading()
                val result = withContext(Dispatchers.IO) { EditorFileIO.load(contentResolver, uri) }
                val name = EditorFileIO.queryName(contentResolver, uri, getString(R.string.untitled))
                val doc = OpenDocument(
                    uri = uri,
                    displayName = name,
                    encoding = result.encoding,
                    text = result.text,
                    isReadOnly = result.isTruncated
                )
                saveCurrentToDoc()
                documents.add(doc)
                currentIndex = documents.size - 1
                loadDocIntoEditor(doc)
                refreshTabBar()
                refreshCurrentView()
                prefs.addRecentFile(uri, name)
                if (result.isTruncated) {
                    showTip(getString(R.string.file_truncated_20mb))
                }
            } catch (e: Exception) {
                hideLoading()
                showTip(getString(R.string.err_open, e.message ?: ""))
            }
        }
    }

    private fun switchToTab(index: Int) {
        if (index == currentIndex || index !in documents.indices) return
        if (prefs.autoSaveEnabled && prefs.autoSaveOnTabSwitch) autoSaveCurrent()
        cancelEditAutoSave()
        cancelSpecialCharRefresh()
        cancelStatusUpdate()
        saveCurrentToDoc()
        currentIndex = index
        loadDocIntoEditor(documents[index])
        refreshTabBar()
        refreshCurrentView()
    }

    private fun requestCloseTab(index: Int) {
        if (index !in documents.indices) return
        val doc = documents[index]
        if (index == currentIndex) saveCurrentToDoc()
        if (!doc.isDirty) {
            closeTabInternal(index)
            return
        }
        EditorDialogs.showUnsaved(
            this,
            onSave = {
                if (index != currentIndex) switchToTab(index)
                pendingAction = { val idx = documents.indexOf(doc); if (idx >= 0) closeTabInternal(idx) }
                saveCurrent()
            },
            onDiscard = { val idx = documents.indexOf(doc); if (idx >= 0) closeTabInternal(idx) }
        )
    }

    private fun closeTabInternal(index: Int) {
        if (index !in documents.indices) return
        documents.removeAt(index)
        if (documents.isEmpty()) {
            newTabInternal(isFirst = true)
            return
        }
        if (currentIndex > index) currentIndex--
        else if (currentIndex == index) currentIndex = currentIndex.coerceAtMost(documents.size - 1)
        loadDocIntoEditor(documents[currentIndex])
        refreshTabBar()
        refreshCurrentView()
    }

    private fun saveCurrentToDoc() {
        val doc = currentDoc() ?: return
        if (doc.isReadOnly || isLargeFileMode) return
        doc.text = binding.editor.text?.toString().orEmpty()
        doc.selStart = binding.editor.selectionStart.coerceAtLeast(0)
        doc.selEnd = binding.editor.selectionEnd.coerceAtLeast(0)
        doc.scrollY = binding.editor.scrollY
    }

    private fun loadDocIntoEditor(doc: OpenDocument) {
        val isLargeLen = doc.text.length > LARGE_FILE_THRESHOLD

        var maxLineLength = 0
        var currentLineLength = 0
        val checkLen = minOf(doc.text.length, 100_000)
        for (i in 0 until checkLen) {
            if (doc.text[i] == '\n') {
                maxLineLength = maxOf(maxLineLength, currentLineLength)
                currentLineLength = 0
                if (maxLineLength > 10000) break
            } else {
                currentLineLength++
            }
        }
        maxLineLength = maxOf(maxLineLength, currentLineLength)

        val isLarge = isLargeLen || maxLineLength > 10000
        isLargeFileMode = isLarge

        applyWrap(if (isLarge) false else prefs.wrap)
        val tf = FontHelper.getTypeface(prefs)
        val textColor = binding.editor.currentTextColor

        if (isLarge) {
            binding.editor.setText("")
            binding.editor.visibility = View.GONE
            binding.largeTextView.visibility = View.VISIBLE

            val textSizePx = prefs.fontSize * resources.displayMetrics.scaledDensity
            binding.largeTextView.setTextAppearance(tf, textSizePx, textColor)

            val theme = EditorThemes.byId(prefs.editorThemeId)
            binding.largeTextView.setBackgroundColor(theme.bg ?: resolveThemeColor(com.google.android.material.R.attr.colorSurface))

            binding.largeTextView.onReady = {
                hideLoading()
            }

            binding.largeTextView.setText(doc.text)
            binding.largeTextView.scrollToY(doc.scrollY)

            binding.fastScrollBar.attachLargeTextView(binding.largeTextView)

            binding.tvLineNumbers.visibility = View.GONE
            showTip(getString(R.string.large_file_long_line_read_only))
        } else {
            binding.largeTextView.visibility = View.GONE
            binding.editor.visibility = View.VISIBLE
            binding.fastScrollBar.attach(binding.editor)

            suppressWatcher = true
            binding.editor.setText(doc.text)
            binding.editor.isReadOnlyMode = doc.isReadOnly
            binding.editor.showSoftInputOnFocus = !doc.isReadOnly

            val s = doc.selStart.coerceIn(0, doc.text.length)
            val e = doc.selEnd.coerceIn(0, doc.text.length)
            binding.editor.setSelection(s, e)
            suppressWatcher = false

            undoManager.reset(doc.text)

            if (doc.text.length > 1_000_000) {
                binding.tvLineNumbers.visibility = View.GONE
                showTip(getString(R.string.large_file_line_numbers_disabled))
            } else {
                binding.tvLineNumbers.visibility =
                    if (prefs.showLineNumbers && !isImmersive) View.VISIBLE else View.GONE
            }

            binding.editor.post {
                binding.editor.scrollTo(0, doc.scrollY)
                if (binding.tvLineNumbers.visibility == View.VISIBLE) {
                    binding.tvLineNumbers.invalidate()
                }
            }
            SpecialCharRenderer.apply(binding.editor, prefs.showSpecialChars)
            triggerStatusUpdate()
            hideLoading()
        }
    }

    private fun refreshTabBar() {
        TabBarRenderer.render(
            context = this, binding = binding, documents = documents, currentIndex = currentIndex,
            onTabClick = { switchToTab(it) }, onTabClose = { requestCloseTab(it) }, onAddClick = { newTab() }
        )
    }

    private fun refreshCurrentView() {
        updateEncodingLabel()
        updateEolLabel()
        if (!isLargeFileMode) {
            triggerStatusUpdate()
        } else {
            updateStatus()
        }
        updateAutoSaveStatus()
    }

    private fun updateLineNumbers() {
        if (isLargeFileMode) return
        binding.tvLineNumbers.invalidate()
    }

    private fun updateStatus() {
        if (isLargeFileMode) {
            val readOnlyText = if (currentDoc()?.isReadOnly == true) "(${getString(R.string.read_only)})" else ""
            val length = currentDoc()?.text?.length ?: 0
            binding.tvStatus.text = "${getString(R.string.large_file_mode)}$readOnlyText · ${getString(R.string.chars_suffix, length)}"
            return
        }
        val text = binding.editor.text ?: return
        val stats = EditorCommands.computeStats(text)
        val items = prefs.statusBarItems.split(",").map { it.trim() }.filter { it.isNotEmpty() }.take(2)
        val parts = ArrayList<String>(items.size)
        for (id in items) {
            val str = when (id) {
                "chars_space" -> getString(R.string.status_chars_space, stats.charsWithSpace)
                "chars_nospace" -> getString(R.string.status_chars_nospace, stats.charsNoSpace)
                "words" -> getString(R.string.status_words, stats.words)
                "lines" -> getString(R.string.status_lines, stats.totalLines)
                "nonempty_lines" -> getString(R.string.status_nonempty_lines, stats.nonEmptyLines)
                "max_line" -> getString(R.string.status_max_line, stats.maxLineWidth)
                "letters" -> getString(R.string.status_letters, stats.letters)
                "invisible" -> getString(R.string.status_invisible, stats.invisibleChars)
                else -> null
            } ?: continue
            parts.add(str)
        }
        binding.tvStatus.text = parts.joinToString(" · ")
    }

    private fun updateEncodingLabel() {
        binding.tvEncoding.text = currentDoc()?.encoding ?: "UTF-8"
    }

    private fun applyWrap(wrap: Boolean) {
        if (isLargeFileMode) {
            binding.editor.setHorizontallyScrolling(true)
            binding.editor.setSingleLine(false)
            binding.editor.maxLines = Int.MAX_VALUE
            return
        }
        binding.editor.setHorizontallyScrolling(!wrap)
        if (!wrap) {
            binding.editor.setSingleLine(false)
            binding.editor.maxLines = Int.MAX_VALUE
        }
    }

    private fun showSpecialCharPanel() {
        val items = listOf(
            getString(R.string.special_lf) to "\n", getString(R.string.special_cr) to "\r",
            getString(R.string.special_crlf) to "\r\n", getString(R.string.special_tab) to "\t",
            getString(R.string.special_zwsp) to "\u200B", getString(R.string.special_zwnj) to "\u200C",
            getString(R.string.special_zwj) to "\u200D", getString(R.string.special_nbsp) to "\u00A0",
            getString(R.string.special_ideographic_space) to "\u3000", getString(R.string.special_nbhyphen) to "\u2011",
            getString(R.string.special_bom) to "\uFEFF", getString(R.string.special_lrm) to "\u200E",
            getString(R.string.special_rlm) to "\u200F"
        )
        EditorDialogs.showSpecialCharPanel(
            activity = this, items = items, showSpecialInitially = prefs.showSpecialChars,
            onToggleShow = { checked -> prefs.showSpecialChars = checked; SpecialCharRenderer.apply(binding.editor, checked) },
            onPick = { value -> insertAtCursor(value) }
        )
    }

    private fun insertAtCursor(text: String) {
        val editor = binding.editor
        val editable = editor.text ?: return
        val s = editor.selectionStart.coerceIn(0, editable.length)
        val e = editor.selectionEnd.coerceIn(0, editable.length)
        val lo = minOf(s, e)
        val hi = maxOf(s, e)
        editable.replace(lo, hi, text)
    }

    private fun undo() {
        if (isLargeFileMode || currentDoc()?.isReadOnly == true) {
            showTip(getString(R.string.no_undo))
            return
        }
        val snap = EditorCommands.undo(undoManager) ?: run { showTip(getString(R.string.no_undo)); return }
        applySnapshot(snap)
    }

    private fun redo() {
        if (isLargeFileMode || currentDoc()?.isReadOnly == true) {
            showTip(getString(R.string.no_redo))
            return
        }
        val snap = EditorCommands.redo(undoManager) ?: run { showTip(getString(R.string.no_redo)); return }
        applySnapshot(snap)
    }

    private fun applySnapshot(snap: Snapshot) {
        suppressWatcher = true
        binding.editor.setText(snap.text)
        val s = snap.selStart.coerceIn(0, snap.text.length)
        val e = snap.selEnd.coerceIn(0, snap.text.length)
        binding.editor.setSelection(s, e)
        suppressWatcher = false
        val doc = currentDoc()
        if (doc != null && !doc.isDirty) {
            doc.isDirty = true
            refreshTabBar()
        }
        if (!isLargeFileMode) {
            triggerStatusUpdate()
            SpecialCharRenderer.apply(binding.editor, prefs.showSpecialChars)
        }
    }

    private fun handleIncomingIntent(intent: Intent?) {
        intent ?: return
        when (intent.action) {
            Intent.ACTION_VIEW, Intent.ACTION_EDIT -> {
                intent.data?.let { EditorFileIO.takePersistable(contentResolver, it); openDocumentIntoNewTab(it) }
            }
            Intent.ACTION_SEND -> {
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                if (!text.isNullOrEmpty()) handleSharedText(text)
            }
        }
    }

    private fun handleSharedText(text: String) {
        val doc = currentDoc()
        if (doc != null && doc.uri == null && !doc.isDirty && doc.text.isEmpty()) {
            doc.text = text
            doc.isDirty = true
            loadDocIntoEditor(doc)
            refreshTabBar()
        } else {
            untitledCounter++
            val nd = OpenDocument(displayName = getString(R.string.untitled_n, untitledCounter), text = text, isDirty = true)
            saveCurrentToDoc()
            documents.add(nd)
            currentIndex = documents.size - 1
            loadDocIntoEditor(nd)
            refreshTabBar()
            refreshCurrentView()
        }
    }

    private fun saveCurrent() {
        val doc = currentDoc() ?: return
        if (doc.isReadOnly || isLargeFileMode) {
            showTip(getString(R.string.cannot_save_large_file))
            return
        }
        if (doc.uri == null) chooseEncodingThenSaveAs()
        else {
            pendingEncoding = doc.encoding
            saveCurrentToUri(doc.uri!!)
        }
    }

    private fun chooseEncodingThenSaveAs() {
        val doc = currentDoc() ?: return
        if (doc.isReadOnly || isLargeFileMode) return
        EditorDialogs.showEncoding(this, doc.encoding) { enc ->
            pendingEncoding = enc
            saveAsLauncher.launch(doc.displayName.ifEmpty { getString(R.string.default_file_name) })
        }
    }

    private fun saveCurrentToUri(uri: Uri) {
        val doc = currentDoc() ?: return
        if (doc.isReadOnly || isLargeFileMode) return
        val content = binding.editor.text?.toString().orEmpty()
        val encoding = pendingEncoding ?: doc.encoding
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) { EditorFileIO.save(contentResolver, uri, content, encoding) }
                doc.uri = uri
                doc.displayName = EditorFileIO.queryName(contentResolver, uri, getString(R.string.untitled))
                doc.encoding = encoding
                doc.text = content
                doc.isDirty = false
                prefs.lastSaveTime = System.currentTimeMillis()
                updateEncodingLabel()
                refreshTabBar()
                updateAutoSaveStatus()
                showTip(getString(R.string.saved))
                prefs.addRecentFile(uri, doc.displayName)
                pendingAction?.also { pendingAction = null }?.invoke()
            } catch (e: Exception) {
                pendingAction = null
                showTip(getString(R.string.err_save, e.message ?: ""))
            } finally {
                pendingEncoding = null
            }
        }
    }

    private fun restoreState(state: Bundle) {
        val restored = InstanceStateStore.restore(state)
        documents.clear()
        documents.addAll(restored.documents)
        if (documents.isEmpty()) return
        currentIndex = restored.currentIndex.coerceIn(0, documents.size - 1)
        untitledCounter = restored.untitledCounter
        loadDocIntoEditor(documents[currentIndex])
    }

    private fun showRecent() {
        val files = prefs.getRecentFiles()
        if (files.isEmpty()) {
            showTip(getString(R.string.no_recent))
            return
        }
        EditorDialogs.showRecent(
            this, files,
            onOpen = { file -> openDocumentIntoNewTab(Uri.parse(file.uri)) },
            onClear = { prefs.clearRecentFiles(); showTip(getString(R.string.recent_cleared)) }
        )
    }

    private fun showFind() {
        EditorDialogs.showFind(this) { query, forward ->
            if (!EditorSearchHelper.findNext(binding.editor, query, forward)) {
                showTip(getString(R.string.not_found))
            }
        }
    }

    private fun showReplace() {
        if (currentDoc()?.isReadOnly == true || isLargeFileMode) {
            showTip(getString(R.string.large_file_feature_disabled))
            return
        }
        EditorDialogs.showReplace(
            this,
            onReplace = { query, replacement -> EditorSearchHelper.replaceOne(binding.editor, query, replacement) },
            onReplaceAll = { query, replacement ->
                val n = EditorSearchHelper.replaceAll(binding.editor, query, replacement)
                if (n == 0) showTip(getString(R.string.not_found)) else showTip(getString(R.string.replaced_n, n))
            }
        )
    }

    private fun showGotoLine() {
        val text = if (isLargeFileMode) currentDoc()?.text else binding.editor.text
        if (text == null) return
        val totalLines = EditorCommands.countLogicalLines(text)
        EditorDialogs.showGotoLine(this, totalLines) { lineNo ->
            val target = EditorCommands.offsetOfLine(text, lineNo)
            if (target < 0) {
                showTip(getString(R.string.goto_line_invalid))
                return@showGotoLine
            }
            if (isLargeFileMode) {
                val approxY = (lineNo * (binding.largeTextView.getContentHeight() / totalLines))
                binding.largeTextView.scrollToY(approxY)
            } else {
                binding.editor.requestFocus()
                binding.editor.setSelection(target)
                binding.editor.forceBringCursorIntoView()
            }
        }
    }

    private fun showStats() {
        if (isLargeFileMode) {
            showTip(getString(R.string.large_file_stats_disabled))
            return
        }
        val doc = currentDoc() ?: return
        val text = binding.editor.text ?: return
        val stats = EditorCommands.computeStats(text)
        EditorDialogs.showStats(this, doc.displayName, stats)
    }

    private fun openDefaultAppsSettings() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(Uri.fromParts("package", packageName, null))
        }
        try {
            startActivity(intent)
        } catch (_: Exception) {
            showTip(getString(R.string.no_settings))
        }
    }
}