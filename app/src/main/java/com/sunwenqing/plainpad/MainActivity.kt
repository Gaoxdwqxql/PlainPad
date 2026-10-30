package com.sunwenqing.plainpad

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
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
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.ListPopupWindow
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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

    companion object {
        const val ACTION_GENERATE_TEST_DOCUMENT = "com.sunwenqing.plainpad.GENERATE_TEST_DOCUMENT"
        const val ACTION_OPEN_READ_ONLY_TEST = "com.sunwenqing.plainpad.OPEN_READ_ONLY_TEST"
        const val EXTRA_TEST_SIZE = "test_size"
        const val EXTRA_TEST_SINGLE_LINE = "test_single_line"
        const val EXTRA_TEST_CHINESE = "test_chinese"
        const val EXTRA_TEST_EOL = "test_eol"
        const val EXTRA_TEST_CONTENT = "test_content"
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: Prefs
    private lateinit var undoManager: UndoManager
    private lateinit var zoomHandler: ZoomGestureHandler
    private lateinit var autoSaveController: AutoSaveController
    private lateinit var statusBarController: StatusBarController
    private lateinit var immersiveController: ImmersiveModeController

    private val documents = mutableListOf<OpenDocument>()
    private var currentIndex = -1

    private var suppressWatcher = false
    private var pendingAction: (() -> Unit)? = null
    private var pendingEncoding: String? = null
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
    private var lastBookmarkIconState: Boolean? = null

    private var statsDialog: AlertDialog? = null

    private val draftHandler = Handler(Looper.getMainLooper())
    private var draftRunnable: Runnable? = null
    private val DRAFT_SAVE_DELAY_MS = 2000L

    private val searchHighlightHandler = Handler(Looper.getMainLooper())
    private var searchHighlightRunnable: Runnable? = null
    private val SEARCH_HIGHLIGHT_DELAY_MS = 220L

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

    // ---------------- 生命周期 ----------------

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

        binding.statusBarFill.setStatusBarHeight()
        binding.navigationBarFill.setNavigationBarHeight()

        ThemeColorHelper.applySystemBars(this)

        undoManager = UndoManager(binding.editor)
        zoomHandler = ZoomGestureHandler(
            initialFontSizeProvider = { prefs.fontSize.toFloat() },
            onPreview = { EditorStyler.applyFontSize(binding, it) },
            onCommit = { commitFontSize(it) }
        )

        autoSaveController = AutoSaveController(
            activity = this,
            prefs = prefs,
            getCurrentDoc = ::currentDoc,
            getContent = ::getEditorContent,
            isLargeFileMode = { isLargeFileMode },
            onSaved = { doc, _ ->
                refreshTabBar()
                statusBarController.updateAutoSaveStatus()
                deleteDraft(doc)
            }
        )
        statusBarController = StatusBarController(
            activity = this,
            binding = binding,
            prefs = prefs,
            getEditor = { binding.editor },
            getCurrentDoc = ::currentDoc,
            isLargeFileMode = { isLargeFileMode }
        )
        immersiveController = ImmersiveModeController(
            activity = this,
            binding = binding,
            prefs = prefs,
            canShowLineNumbers = { prefs.showLineNumbers && !isLargeFileMode }
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

        binding.root.postDelayed({ onStartupChecks() }, 600L)
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
        binding.editor.currentLineHighlightEnabled = prefs.currentLineHighlight

        binding.tvLineNumbers.visibility =
            if (prefs.showLineNumbers && !immersiveController.isImmersive && !isLargeFileMode)
                View.VISIBLE else View.GONE

        if (!isLargeFileMode) {
            statusBarController.triggerStatusUpdate()
            statusBarController.scheduleSpecialCharRefresh()
        }
        statusBarController.updateAutoSaveStatus()
        scheduleAutoSave()
    }

    override fun onPause() {
        statsDialog?.dismiss()
        statsDialog = null

        if (uiReady) {
            clearSearchHighlight()
            saveCurrentToDoc()
            if (prefs.autoSaveEnabled && prefs.autoSaveOnPause) {
                autoSaveController.autoSaveCurrent()
            }
            if (documents.isNotEmpty()) {
                prefs.saveTabs(documents, sync = true)
            }
            DraftManager.clearAll(this)
        }
        cancelDraftSave()
        if (::autoSaveController.isInitialized) {
            autoSaveController.cancelAll()
        }
        if (::statusBarController.isInitialized) {
            statusBarController.cancelAll()
        }
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
        if (::autoSaveController.isInitialized) {
            autoSaveController.cancelAll()
        }
        if (::statusBarController.isInitialized) {
            statusBarController.cancelAll()
        }
        cancelDraftSave()
        if (uiReady && ::undoManager.isInitialized) {
            undoManager.release()
        }
        super.onDestroy()
    }

    // ---------------- 编辑器与 UI 设置 ----------------

    private fun setupEditor() {
        EditorStyler.applyFontSize(binding, prefs.fontSize.toFloat())
        binding.editor.currentLineHighlightEnabled = prefs.currentLineHighlight
        binding.editor.selectionChangedListener = { updateBookmarkButtonState() }
        binding.editor.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {
                // 编辑时让书签偏移跟随文本增删移动
                if (!suppressWatcher && !isLargeFileMode) {
                    currentDoc()?.let { adjustBookmarks(it, a, b, c) }
                }
            }
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
                    autoSaveController.scheduleEditAutoSave()
                    if (prefs.showSpecialChars) {
                        statusBarController.scheduleSpecialCharRefresh()
                    }
                    statusBarController.triggerStatusUpdate()
                    scheduleDraftSave()
                }
                statusBarController.updateAutoSaveStatus()
                if (!isLargeFileMode) binding.tvLineNumbers.post { refreshBookmarkMarkers() }
            }
        })
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
                if (immersiveController.isImmersive) {
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
        // 底部书签按钮：单击开关当前行书签，长按打开管理面板
        binding.btnBookmark.setOnClickListener { toggleBookmarkAtCursor() }
        binding.btnBookmark.setOnLongClickListener {
            showBookmarks()
            true
        }
    }

    private fun applyAppearanceIfChanged() {
        val themeChanged = prefs.editorThemeId != lastAppliedEditorTheme
        val fontChanged = prefs.fontSize != lastAppliedFontSize ||
                prefs.editorFontFamily != lastAppliedFontFamily

        if (themeChanged || fontChanged) {
            if (!isLargeFileMode) {
                EditorStyler.applyAll(binding, prefs)
                binding.editor.refreshCurrentLineHighlight()
            } else {
                val theme = EditorThemes.byId(prefs.editorThemeId)
                EditorThemes.apply(theme, this, binding.editArea, binding.editor, binding.tvLineNumbers)
                val tf = FontHelper.getTypeface(this, prefs)
                val textColor = binding.editor.currentTextColor
                val textSizePx = prefs.fontSize * resources.displayMetrics.scaledDensity
                binding.largeTextView.setTextAppearance(tf, textSizePx, textColor)
                binding.largeTextView.setBackgroundColor(
                    theme.bg ?: resolveThemeColor(com.google.android.material.R.attr.colorSurface)
                )
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

    // ---------------- Controller 包装 ----------------

    private fun triggerStatusUpdate() = statusBarController.triggerStatusUpdate()

    private fun updateStatus() = statusBarController.updateStatus()

    private fun updateEncodingLabel() = statusBarController.updateEncodingLabel()

    private fun updateLineNumbers() = statusBarController.updateLineNumbers()

    private fun updateAutoSaveStatus() = statusBarController.updateAutoSaveStatus()

    private fun updateEolLabel() {
        currentEol = statusBarController.updateEolLabel()
    }

    private fun scheduleSpecialCharRefresh() = statusBarController.scheduleSpecialCharRefresh()

    private fun cancelSpecialCharRefresh() = statusBarController.cancelSpecialCharRefresh()

    private fun cancelStatusUpdate() = statusBarController.cancelStatusUpdate()

    private fun scheduleAutoSave() = autoSaveController.scheduleInterval()

    private fun autoSaveCurrent() = autoSaveController.autoSaveCurrent()

    private fun scheduleEditAutoSave() = autoSaveController.scheduleEditAutoSave()

    private fun cancelEditAutoSave() = autoSaveController.cancelEditAutoSave()

    private fun applyImmersive(on: Boolean) = immersiveController.apply(on)

    // ---------------- 草稿备份 ----------------

    private fun scheduleDraftSave() {
        cancelDraftSave()
        val doc = currentDoc() ?: return
        if (doc.isReadOnly || isLargeFileMode) return
        if (!doc.isDirty) return

        val targetId = doc.draftId
        val r = Runnable {
            val current = documents.firstOrNull { it.draftId == targetId } ?: return@Runnable
            if (current == currentDoc()) {
                current.text = binding.editor.text?.toString().orEmpty()
                current.selStart = binding.editor.selectionStart.coerceAtLeast(0)
                current.selEnd = binding.editor.selectionEnd.coerceAtLeast(0)
                current.scrollY = binding.editor.scrollY
            }
            lifecycleScope.launch(Dispatchers.IO) {
                DraftManager.save(this@MainActivity, current)
            }
        }
        draftRunnable = r
        draftHandler.postDelayed(r, DRAFT_SAVE_DELAY_MS)
    }

    private fun cancelDraftSave() {
        draftRunnable?.let { draftHandler.removeCallbacks(it) }
        draftRunnable = null
    }

    private fun saveDraftNow(doc: OpenDocument) {
        if (doc.isReadOnly || isLargeFileMode) return
        if (!doc.isDirty) return
        lifecycleScope.launch(Dispatchers.IO) {
            DraftManager.save(this@MainActivity, doc)
        }
    }

    private fun deleteDraft(doc: OpenDocument) {
        lifecycleScope.launch(Dispatchers.IO) {
            DraftManager.delete(this@MainActivity, doc.draftId)
        }
    }

    // ---------------- 崩溃恢复 ----------------

    /** 启动后的异步检查：崩溃日志优先，没有崩溃提示再查草稿 */
    private fun onStartupChecks() {
        if (isFinishing || isDestroyed) return
        if (!checkCrashesOnStartup()) checkDraftsOnStartup()
    }

    /** @return true 表示弹出了崩溃提示（草稿检查交给弹窗关闭回调） */
    private fun checkCrashesOnStartup(): Boolean {
        val files = CrashLogger.list(this)
        if (files.isEmpty()) return false

        val latest = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
            .format(Date(files.first().lastModified()))

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.crash_title)
            .setMessage(getString(R.string.crash_message, files.size, latest))
            .setPositiveButton(R.string.close, null)
            .setNeutralButton(R.string.crash_copy) { _, _ ->
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("PlainPad crash log", CrashLogger.readAll(this)))
                showTip(getString(R.string.crash_copied))
            }
            .setNegativeButton(R.string.crash_clear) { _, _ ->
                CrashLogger.clear(this)
                showTip(getString(R.string.crash_cleared))
            }
            .setOnDismissListener { checkDraftsOnStartup() }
            .show()
        return true
    }

    private fun checkDraftsOnStartup() {
        if (isFinishing || isDestroyed) return
        val drafts = DraftManager.listAll(this)
        if (drafts.isEmpty()) return

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.draft_recovery_title)
            .setMessage(getString(R.string.draft_recovery_message, drafts.size))
            .setPositiveButton(R.string.draft_recovery_restore) { _, _ ->
                restoreDrafts(drafts)
            }
            .setNegativeButton(R.string.draft_recovery_discard) { _, _ ->
                DraftManager.clearAll(this)
            }
            .setCancelable(false)
            .show()
    }

    private fun restoreDrafts(drafts: List<DraftInfo>) {
        if (drafts.isEmpty()) return
        saveCurrentToDoc()
        cancelDraftSave()

        var restored = 0
        var lastRestoredIndex = -1

        for (draft in drafts) {
            val loaded = DraftManager.load(this, draft.draftId) ?: continue
            val (info, text) = loaded

            val existingIdx = documents.indexOfFirst { it.draftId == info.draftId }
            if (existingIdx >= 0) {
                val doc = documents[existingIdx]
                doc.text = text
                doc.selStart = info.selStart
                doc.selEnd = info.selEnd
                doc.scrollY = info.scrollY
                doc.hasBom = info.hasBom
                doc.bookmarks = info.bookmarks
                doc.isDirty = true
                if (existingIdx == currentIndex) lastRestoredIndex = existingIdx
            } else {
                val doc = OpenDocument(
                    uri = info.uri?.let { Uri.parse(it) },
                    displayName = info.displayName,
                    encoding = info.encoding,
                    text = text,
                    selStart = info.selStart,
                    selEnd = info.selEnd,
                    scrollY = info.scrollY,
                    isReadOnly = info.isReadOnly,
                    isDirty = true,
                    hasBom = info.hasBom,
                    bookmarks = info.bookmarks,
                    draftId = info.draftId
                )
                documents.add(doc)
                lastRestoredIndex = documents.size - 1
            }
            restored++
        }

        if (restored == 0) {
            DraftManager.clearAll(this)
            showTip(getString(R.string.draft_recovery_nothing))
            return
        }

        if (currentIndex == -1 && documents.isNotEmpty()) currentIndex = 0
        if (lastRestoredIndex >= 0) currentIndex = lastRestoredIndex

        refreshTabBar()
        loadDocIntoEditor(documents[currentIndex])
        refreshCurrentView()
        showTip(getString(R.string.draft_recovery_restored, restored))
    }

    // ---------------- 文档管理 ----------------

    private fun currentDoc(): OpenDocument? = documents.getOrNull(currentIndex)

    private fun getEditorContent(): String = binding.editor.text?.toString().orEmpty()

    private fun newTab() {
        if (prefs.plusReuseEmpty) {
            val current = currentDoc()
            val alreadyOnEmpty =
                current != null && current.uri == null && !current.isDirty && current.text.isEmpty()
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
        val name = if (isFirst && documents.isEmpty()) getString(R.string.untitled)
        else getString(R.string.untitled_n, untitledCounter)
        val doc = OpenDocument(displayName = name)

        val prevDoc = currentDoc()
        saveCurrentToDoc()
        if (prevDoc != null) saveDraftNow(prevDoc)

        documents.add(doc)
        currentIndex = documents.size - 1
        loadDocIntoEditor(doc)
        refreshTabBar()
        refreshCurrentView()
    }

    private fun switchToTab(index: Int) {
        if (index == currentIndex || index !in documents.indices) return
        statsDialog?.dismiss()
        statsDialog = null
        clearSearchHighlight()

        if (prefs.autoSaveEnabled && prefs.autoSaveOnTabSwitch) autoSaveCurrent()
        cancelEditAutoSave()
        cancelDraftSave()
        cancelSpecialCharRefresh()
        cancelStatusUpdate()

        val prevDoc = currentDoc()
        saveCurrentToDoc()
        if (prevDoc != null) saveDraftNow(prevDoc)

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
        val doc = documents[index]
        deleteDraft(doc)
        if (index == currentIndex) cancelDraftSave()

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
        val tf = FontHelper.getTypeface(this, prefs)
        val textColor = binding.editor.currentTextColor

        if (isLarge) {
            binding.editor.setText("")
            binding.editor.visibility = View.GONE
            binding.largeTextView.visibility = View.VISIBLE

            val textSizePx = prefs.fontSize * resources.displayMetrics.scaledDensity
            binding.largeTextView.setTextAppearance(tf, textSizePx, textColor)

            val theme = EditorThemes.byId(prefs.editorThemeId)
            binding.largeTextView.setBackgroundColor(
                theme.bg ?: resolveThemeColor(com.google.android.material.R.attr.colorSurface)
            )

            binding.largeTextView.onReady = { hideLoading() }

            binding.largeTextView.setText(doc.text)
            binding.largeTextView.scrollToY(doc.scrollY)

            binding.fastScrollBar.attachLargeTextView(binding.largeTextView)

            binding.tvLineNumbers.visibility = View.GONE
            binding.editor.setBookmarkedLines(emptySet())
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
                    if (prefs.showLineNumbers && !immersiveController.isImmersive)
                        View.VISIBLE else View.GONE
            }

            binding.editor.post {
                binding.editor.scrollTo(0, doc.scrollY)
                if (binding.tvLineNumbers.visibility == View.VISIBLE) {
                    binding.tvLineNumbers.invalidate()
                }
                refreshBookmarkMarkers()
            }
            SpecialCharRenderer.apply(binding.editor, prefs.showSpecialChars)
            triggerStatusUpdate()
            hideLoading()
        }
    }

    private fun refreshTabBar() {
        TabBarRenderer.render(
            context = this, binding = binding, documents = documents, currentIndex = currentIndex,
            onTabClick = { switchToTab(it) },
            onTabClose = { requestCloseTab(it) },
            onAddClick = { newTab() }
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

    // ---------------- 启动 / 恢复 ----------------

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
                        val result = withContext(Dispatchers.IO) {
                            EditorFileIO.load(contentResolver, doc.uri!!)
                        }
                        doc.text = result.text
                        doc.encoding = result.encoding
                        doc.isReadOnly = result.isTruncated
                        doc.hasBom = result.hasBom
                        if (index == currentIndex) loadDocIntoEditor(doc)
                        refreshTabBar()
                    } catch (_: Exception) {}
                }
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

    // ---------------- 打开 / 保存 ----------------

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
                    isReadOnly = result.isTruncated,
                    hasBom = result.hasBom
                )
                saveCurrentToDoc()
                documents.add(doc)
                currentIndex = documents.size - 1
                loadDocIntoEditor(doc)
                refreshTabBar()
                refreshCurrentView()
                prefs.addRecentFile(uri, name)
                if (result.isTruncated) showTip(getString(R.string.file_truncated_20mb))
            } catch (e: Exception) {
                hideLoading()
                showTip(getString(R.string.err_open, e.message ?: ""))
            }
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
        val withBom = prefs.utf8Bom || doc.hasBom
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    EditorFileIO.save(contentResolver, uri, content, encoding, withBom)
                }
                doc.uri = uri
                doc.displayName = EditorFileIO.queryName(contentResolver, uri, getString(R.string.untitled))
                doc.encoding = encoding
                doc.text = content
                doc.hasBom = withBom
                doc.isDirty = false
                prefs.lastSaveTime = System.currentTimeMillis()
                deleteDraft(doc)
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
                    val withBom = prefs.utf8Bom || doc.hasBom
                    withContext(Dispatchers.IO) {
                        EditorFileIO.save(contentResolver, doc.uri!!, doc.text, doc.encoding, withBom)
                    }
                    doc.hasBom = withBom
                    doc.isDirty = false
                    DraftManager.delete(this@MainActivity, doc.draftId)
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

    // ---------------- 以指定编码重新打开 ----------------

    private fun showReopenEncoding() {
        val doc = currentDoc() ?: return
        val uri = doc.uri
        if (uri == null) {
            showTip(getString(R.string.reopen_no_file))
            return
        }
        EditorDialogs.showEncoding(this, doc.encoding) { enc ->
            if (doc.isDirty) {
                MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.reopen_discard_title)
                    .setMessage(R.string.reopen_discard_message)
                    .setPositiveButton(R.string.ok) { _, _ -> reopenWithEncoding(doc, uri, enc) }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            } else {
                reopenWithEncoding(doc, uri, enc)
            }
        }
    }

    private fun reopenWithEncoding(doc: OpenDocument, uri: Uri, encoding: String) {
        lifecycleScope.launch {
            try {
                showLoading()
                val result = withContext(Dispatchers.IO) {
                    EditorFileIO.loadWithEncoding(contentResolver, uri, encoding)
                }
                doc.text = result.text
                doc.encoding = encoding
                doc.isReadOnly = result.isTruncated
                doc.hasBom = result.hasBom
                doc.isDirty = false
                doc.selStart = 0
                doc.selEnd = 0
                doc.scrollY = 0
                if (doc === currentDoc()) {
                    loadDocIntoEditor(doc)
                }
                refreshTabBar()
                refreshCurrentView()
                hideLoading()
                if (result.isTruncated) {
                    showTip(getString(R.string.file_truncated_20mb))
                } else {
                    showTip(getString(R.string.reopen_done, encoding))
                }
            } catch (e: Exception) {
                hideLoading()
                showTip(getString(R.string.err_open, e.message ?: ""))
            }
        }
    }

    // ---------------- 菜单 ----------------

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

    private fun showMoreMenu() {
        showCustomMenu(
            binding.btnMore,
            listOf(
                getString(R.string.menu_new) to { newTab() },
                getString(R.string.menu_open) to { openLauncher.launch(arrayOf("text/plain", "text/*")) },
                getString(R.string.menu_reopen_encoding) to { showReopenEncoding() },
                getString(R.string.menu_recent) to { showRecent() },
                getString(R.string.menu_close_tab) to { requestCloseTab(currentIndex) },
                getString(R.string.menu_replace) to { showReplace() },
                getString(R.string.menu_goto_line) to { showGotoLine() },
                getString(R.string.menu_bookmarks) to { showBookmarks() },
                getString(R.string.menu_immersive) to { applyImmersive(true) },
                getString(R.string.menu_default) to { openDefaultAppsSettings() },
                getString(R.string.menu_help) to { startActivity(Intent(this, HelpActivity::class.java)) },
                getString(R.string.menu_about) to { startActivity(Intent(this, AboutActivity::class.java)) }
            )
        )
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
            setStroke(
                (1 * density).toInt(),
                resolveThemeColor(com.google.android.material.R.attr.colorOutlineVariant)
            )
        }
        popup.setBackgroundDrawable(
            InsetDrawable(
                bg,
                (4 * density).toInt(),
                (4 * density).toInt(),
                (4 * density).toInt(),
                (4 * density).toInt()
            )
        )
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
            if (tv.resourceId != 0) androidx.core.content.ContextCompat.getColor(this, tv.resourceId)
            else tv.data
        } else 0xFFEEEEEE.toInt()
    }

    // ---------------- EOL ----------------

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

        val newText = EolConverter.convert(text, targetEol)
        if (newText == null) {
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
        scheduleDraftSave()

        val newSel = selStart.coerceIn(0, newText.length)
        editor.setSelection(newSel)

        updateEolLabel()
        triggerStatusUpdate()
        showTip(getString(R.string.converted_to_format, targetLabel))
    }

    // ---------------- 查找 / 替换 / 跳转 / 统计 ----------------

    private fun showFind() {
        if (isLargeFileMode) {
            showTip(getString(R.string.large_file_feature_disabled))
            return
        }
        EditorDialogs.showFind(
            this,
            onFind = { query, forward, options, isFirst ->
                EditorSearchHelper.findNext(binding.editor, query, forward, options, isFirst)
            },
            onQueryChanged = { query, options -> scheduleSearchHighlight(query, options) },
            onClosed = { clearSearchHighlight() }
        )
    }

    private fun showReplace() {
        if (currentDoc()?.isReadOnly == true || isLargeFileMode) {
            showTip(getString(R.string.large_file_feature_disabled))
            return
        }
        EditorDialogs.showReplace(
            this,
            onReplace = { query, replacement, options, isFirst ->
                EditorSearchHelper.replaceOne(binding.editor, query, replacement, options, isFirst)
            },
            onReplaceAll = { query, replacement, options ->
                EditorSearchHelper.replaceAll(binding.editor, query, replacement, options)
            },
            onQueryChanged = { query, options -> scheduleSearchHighlight(query, options) },
            onClosed = { clearSearchHighlight() }
        )
    }

    // ---------------- 查找高亮 ----------------

    private fun scheduleSearchHighlight(query: String, options: SearchOptions) {
        cancelSearchHighlight()
        val r = Runnable {
            searchHighlightRunnable = null
            if (isLargeFileMode) return@Runnable
            SearchHighlighter.apply(binding.editor, query, options, searchHighlightColor())
        }
        searchHighlightRunnable = r
        searchHighlightHandler.postDelayed(r, SEARCH_HIGHLIGHT_DELAY_MS)
    }

    private fun cancelSearchHighlight() {
        searchHighlightRunnable?.let { searchHighlightHandler.removeCallbacks(it) }
        searchHighlightRunnable = null
    }

    private fun clearSearchHighlight() {
        cancelSearchHighlight()
        SearchHighlighter.clear(binding.editor)
    }

    /** 半透明主题色，和当前行高亮区分开 */
    private fun searchHighlightColor(): Int {
        val primary = resolveThemeColor(com.google.android.material.R.attr.colorPrimary)
        return (primary and 0x00FFFFFF) or 0x33000000
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
                val approxY = lineNo * (binding.largeTextView.getContentHeight() / totalLines)
                binding.largeTextView.scrollToY(approxY)
            } else {
                binding.editor.requestFocus()
                binding.editor.setSelection(target)
                binding.editor.forceBringCursorIntoView()
            }
        }
    }

    // ---------------- 书签 ----------------

    /** 光标所在视觉行的行首偏移 */
    private fun currentLineStartOffset(): Int? {
        val editor = binding.editor
        val len = editor.text?.length ?: return null
        val layout = editor.layout ?: return null
        val cursor = editor.selectionEnd.coerceIn(0, len)
        return layout.getLineStart(layout.getLineForOffset(cursor))
    }

    private fun showBookmarks() {
        if (currentDoc() == null) return
        if (isLargeFileMode) {
            showTip(getString(R.string.large_file_feature_disabled))
            return
        }
        EditorDialogs.showBookmarks(
            this,
            provider = { bookmarkRows() },
            hasCurrent = { hasBookmarkOnCurrentLine() },
            onJump = { row -> jumpToBookmark(row.offset) },
            onToggleCurrent = { toggleBookmarkAtCursor() },
            onClearAll = {
                currentDoc()?.bookmarks = emptyList()
                refreshBookmarkMarkers()
                scheduleDraftSave()
            },
            onDelete = { row -> deleteBookmark(row.offset) },
            onPrev = { jumpToAdjacentBookmark(-1) },
            onNext = { jumpToAdjacentBookmark(1) }
        )
    }

    /** 把当前文档的书签翻译成列表行（行号 + 行首预览） */
    private fun bookmarkRows(): List<EditorDialogs.BookmarkRow> {
        val doc = currentDoc() ?: return emptyList()
        val text = binding.editor.text ?: return emptyList()
        val layout = binding.editor.layout ?: return emptyList()
        return doc.bookmarks.sorted().map { off ->
            val safe = off.coerceIn(0, text.length)
            EditorDialogs.BookmarkRow(
                line = layout.getLineForOffset(safe) + 1,
                preview = linePreview(text, safe),
                offset = off
            )
        }
    }

    private fun hasBookmarkOnCurrentLine(): Boolean {
        val doc = currentDoc() ?: return false
        val offset = currentLineStartOffset() ?: return false
        return doc.bookmarks.contains(offset)
    }

    private fun deleteBookmark(offset: Int) {
        val doc = currentDoc() ?: return
        doc.bookmarks = doc.bookmarks.filter { it != offset }
        refreshBookmarkMarkers()
        scheduleDraftSave()
        showTip(getString(R.string.bookmark_removed))
    }

    /** direction = 1 下一个，-1 上一个；到头会循环 */
    private fun jumpToAdjacentBookmark(direction: Int) {
        val doc = currentDoc() ?: return
        val sorted = doc.bookmarks.sorted()
        if (sorted.isEmpty()) {
            showTip(getString(R.string.bookmark_empty))
            return
        }
        val len = binding.editor.text?.length ?: 0
        val cursor = binding.editor.selectionStart.coerceIn(0, len)
        val target = if (direction > 0) {
            sorted.firstOrNull { it > cursor } ?: sorted.first()
        } else {
            sorted.lastOrNull { it < cursor } ?: sorted.last()
        }
        jumpToBookmark(target)
    }

    /** 取书签所在行的开头一小段文字作为预览 */
    private fun linePreview(text: CharSequence, offset: Int): String {
        val end = text.indexOf('\n', offset).let { if (it < 0) text.length else it }
        val raw = text.subSequence(offset, end).toString().trim()
        return if (raw.length > 40) raw.take(40) + "…" else raw
    }

    private fun toggleBookmarkAtCursor() {
        val doc = currentDoc() ?: return
        if (isLargeFileMode) {
            showTip(getString(R.string.large_file_feature_disabled))
            return
        }
        val offset = currentLineStartOffset() ?: return
        val list = doc.bookmarks.toMutableList()
        val removed = list.remove(offset)
        if (!removed) {
            list.add(offset)
            list.sort()
        }
        doc.bookmarks = list
        refreshBookmarkMarkers()
        scheduleDraftSave()
        showTip(getString(if (removed) R.string.bookmark_removed else R.string.bookmark_added))
    }

    private fun jumpToBookmark(offset: Int) {
        val editor = binding.editor
        val len = editor.text?.length ?: 0
        val target = offset.coerceIn(0, len)
        editor.requestFocus()
        editor.setSelection(target)
        editor.forceBringCursorIntoView()
    }

    /** 把书签偏移换算成视觉行号，同时同步底部书签按钮的填充状态 */
    private fun refreshBookmarkMarkers() {
        val doc = currentDoc()
        val layout = binding.editor.layout
        if (doc == null || isLargeFileMode || layout == null) {
            binding.editor.setBookmarkedLines(emptySet())
            updateBookmarkButtonState()
            return
        }
        val len = binding.editor.text?.length ?: 0
        val lines = doc.bookmarks
            .filter { it in 0..len }
            .map { layout.getLineForOffset(it) }
            .toSet()
        binding.editor.setBookmarkedLines(lines)
        updateBookmarkButtonState()
    }

    /** 光标行的书签状态变化时切换实心/空心图标 */
    private fun updateBookmarkButtonState() {
        if (!::binding.isInitialized) return
        val has = !isLargeFileMode && hasBookmarkOnCurrentLine()
        if (lastBookmarkIconState == has) return
        lastBookmarkIconState = has
        binding.btnBookmark.setImageResource(
            if (has) R.drawable.ic_bookmark_filled else R.drawable.ic_bookmark
        )
    }

    /** 文本增删后修正书签偏移：编辑点之前不动，落在删除区间内的收拢到编辑点 */
    private fun adjustBookmarks(doc: OpenDocument, start: Int, before: Int, count: Int) {
        if (doc.bookmarks.isEmpty()) return
        val delta = count - before
        val updated = ArrayList<Int>(doc.bookmarks.size)
        for (b in doc.bookmarks) {
            val nb = when {
                b <= start -> b
                b < start + before -> start
                else -> b + delta
            }
            if (nb !in updated) updated.add(nb)
        }
        doc.bookmarks = updated
    }

    private fun showStats() {
        if (isLargeFileMode) {
            showTip(getString(R.string.large_file_stats_disabled))
            return
        }
        val doc = currentDoc() ?: return
        val text = binding.editor.text ?: return
        val stats = EditorCommands.computeStats(text)
        val snapshot = text.toString()

        val hasUri = doc.uri != null
        val wasDirty = doc.isDirty

        statsDialog?.dismiss()
        statsDialog = EditorDialogs.showStats(
            activity = this,
            fileName = doc.displayName,
            stats = stats,
            computeSha256 = {
                withContext(Dispatchers.IO) {
                    val hash = FileHasher.sha256OfText(snapshot)
                    if (hasUri && !wasDirty) {
                        Sha256Result.DiskFile(hash)
                    } else {
                        Sha256Result.CurrentContent(hash)
                    }
                }
            }
        )
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

    // ---------------- 特殊字符 / 撤销 / 缩放 ----------------

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
            onToggleShow = { checked ->
                prefs.showSpecialChars = checked
                SpecialCharRenderer.apply(binding.editor, checked)
            },
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
        scheduleDraftSave()
        if (!isLargeFileMode) {
            triggerStatusUpdate()
            SpecialCharRenderer.apply(binding.editor, prefs.showSpecialChars)
        }
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

    // ---------------- Incoming Intent ----------------

    private fun handleIncomingIntent(intent: Intent?) {
        intent ?: return
        when (intent.action) {
            ACTION_GENERATE_TEST_DOCUMENT -> {
                handleDevGenerateTest(intent)
            }
            ACTION_OPEN_READ_ONLY_TEST -> {
                handleDevReadOnlyDoc(intent)
            }
            Intent.ACTION_VIEW, Intent.ACTION_EDIT -> {
                intent.data?.let {
                    EditorFileIO.takePersistable(contentResolver, it)
                    openDocumentIntoNewTab(it)
                }
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
            val nd = OpenDocument(
                displayName = getString(R.string.untitled_n, untitledCounter),
                text = text,
                isDirty = true
            )
            saveCurrentToDoc()
            documents.add(nd)
            currentIndex = documents.size - 1
            loadDocIntoEditor(nd)
            refreshTabBar()
            refreshCurrentView()
        }
    }

    // ---------------- 开发者测试文档 ----------------

    private fun handleDevGenerateTest(intent: Intent) {
        val size = intent.getLongExtra(EXTRA_TEST_SIZE, 0L)
        val singleLine = intent.getBooleanExtra(EXTRA_TEST_SINGLE_LINE, false)
        val chinese = intent.getBooleanExtra(EXTRA_TEST_CHINESE, false)
        val eol = intent.getStringExtra(EXTRA_TEST_EOL)

        if (size <= 0L || size > 20L * 1024 * 1024) {
            showTip("Invalid test size")
            return
        }

        lifecycleScope.launch {
            showLoading()
            try {
                val text = withContext(Dispatchers.IO) {
                    val raw = generateTestText(size, singleLine, chinese)
                    when (eol) {
                        "\r\n" -> raw.replace("\n", "\r\n")
                        "mixed" -> buildMixedEol(raw)
                        else -> raw
                    }
                }
                val name = buildString {
                    append("test_").append(size / 1024).append("kb")
                    if (singleLine) append("_single")
                    if (chinese) append("_cn")
                    when (eol) {
                        "\r\n" -> append("_crlf")
                        "mixed" -> append("_mixed")
                    }
                    append(".txt")
                }
                val doc = OpenDocument(
                    displayName = name,
                    text = text,
                    isDirty = true
                )
                saveCurrentToDoc()
                documents.add(doc)
                currentIndex = documents.size - 1
                loadDocIntoEditor(doc)
                refreshTabBar()
                refreshCurrentView()
                hideLoading()
            } catch (e: Exception) {
                hideLoading()
                showTip("Generate failed: ${e.message}")
            }
        }
    }

    private fun handleDevReadOnlyDoc(intent: Intent) {
        val content = intent.getStringExtra(EXTRA_TEST_CONTENT).orEmpty()
        if (content.isEmpty()) {
            showTip("Empty content")
            return
        }
        val doc = OpenDocument(
            displayName = "readonly_test_${System.currentTimeMillis()}.txt",
            text = content,
            isReadOnly = true,
            isDirty = false
        )
        saveCurrentToDoc()
        documents.add(doc)
        currentIndex = documents.size - 1
        loadDocIntoEditor(doc)
        refreshTabBar()
        refreshCurrentView()
    }

    private fun generateTestText(size: Long, singleLine: Boolean, chinese: Boolean): String {
        val targetLen = size.toInt().coerceAtLeast(1)
        val sb = StringBuilder(targetLen + 1024)

        if (singleLine) {
            val chunk = if (chinese) {
                "这是一个用于测试大文件单行文本处理能力的超长文本，用于验证虚拟列表渲染、输入法拦截、只读保护等功能。"
            } else {
                "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789 "
            }
            while (sb.length < targetLen) {
                sb.append(chunk)
            }
        } else {
            var n = 1
            val fmt = if (chinese) {
                "这是第 %d 行测试文本，用于验证多行编辑、查找替换、行号显示、统计功能等场景。"
            } else {
                "Line %d: The quick brown fox jumps over the lazy dog. 0123456789"
            }
            while (sb.length < targetLen) {
                sb.append(String.format(Locale.US, fmt, n)).append('\n')
                n++
            }
        }

        return if (sb.length > targetLen) sb.substring(0, targetLen) else sb.toString()
    }

    private fun buildMixedEol(text: String): String {
        val sb = StringBuilder(text.length + 1024)
        var lineNo = 0
        text.forEach { c ->
            if (c == '\n') {
                lineNo++
                if (lineNo % 2 == 0) sb.append("\r\n") else sb.append('\n')
            } else {
                sb.append(c)
            }
        }
        return sb.toString()
    }

    // ---------------- 通用辅助 ----------------

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

    private fun showTip(msg: String) {
        val anchor =
            if (immersiveController.isImmersive) binding.btnExitImmersive else binding.statusBar
        Snackbar.make(binding.root, msg, Snackbar.LENGTH_SHORT).setAnchorView(anchor).show()
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
        } catch (_: Exception) {
            showTip(getString(R.string.no_settings))
        }
    }
}