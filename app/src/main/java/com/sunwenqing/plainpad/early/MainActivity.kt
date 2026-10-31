package com.sunwenqing.plainpad.early

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.sunwenqing.plainpad.early.databinding.ActivityMainBinding
import com.sunwenqing.plainpad.early.databinding.DialogFindReplaceBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: Prefs

    private var currentUri: Uri? = null
    private var currentName: String? = null
    private var isDirty = false
    private var suppressWatcher = false
    private var pendingAction: (() -> Unit)? = null

    private val openLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@registerForActivityResult
            takePersistable(uri)
            loadFromUri(uri)
        }

    private val saveAsLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
            if (uri == null) {
                pendingAction = null
                return@registerForActivityResult
            }
            takePersistable(uri)
            saveToUri(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        prefs = Prefs(this)
        setupEditor()
        setupBackHandler()

        if (savedInstanceState != null) {
            restoreState(savedInstanceState)
        } else {
            handleIncomingIntent(intent)
        }

        applyWrap(prefs.wrap)
        updateTitle()
        updateStatus()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_TEXT, binding.editor.text?.toString().orEmpty())
        outState.putString(KEY_URI, currentUri?.toString())
        outState.putString(KEY_NAME, currentName)
        outState.putBoolean(KEY_DIRTY, isDirty)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        intent ?: return
        when (intent.action) {
            Intent.ACTION_VIEW, Intent.ACTION_EDIT -> {
                intent.data?.let {
                    takePersistable(it)
                    loadFromUri(it)
                }
            }
            Intent.ACTION_SEND -> {
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                if (!text.isNullOrEmpty()) {
                    suppressWatcher = true
                    binding.editor.setText(text)
                    suppressWatcher = false
                    currentUri = null
                    currentName = null
                    isDirty = true
                    updateTitle()
                    updateStatus()
                }
            }
        }
    }

    private fun setupEditor() {
        binding.editor.setTextSize(TypedValue.COMPLEX_UNIT_SP, prefs.fontSize.toFloat())
        binding.editor.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (!suppressWatcher) markDirty()
                updateStatus()
            }
        })
    }

    private fun setupBackHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                askSaveThen { finish() }
            }
        })
    }

    private fun applyWrap(wrap: Boolean) {
        binding.editor.setHorizontallyScrolling(!wrap)
        if (!wrap) {
            binding.editor.setSingleLine(false)
            binding.editor.maxLines = Int.MAX_VALUE
        }
    }

    private fun markDirty() {
        if (!isDirty) {
            isDirty = true
            updateTitle()
        }
    }

    private fun updateTitle() {
        val name = currentName ?: getString(R.string.untitled)
        supportActionBar?.title = if (isDirty) "$name *" else name
    }

    private fun updateStatus() {
        val t = binding.editor.text ?: return
        var lines = 1
        for (c in t) if (c == '\n') lines++
        binding.tvStatus.text = getString(R.string.status_count, t.length, lines)
    }

    private fun loadFromUri(uri: Uri) {
        lifecycleScope.launch {
            try {
                val text = withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.use {
                        TextCodec.decode(it.readBytes())
                    } ?: throw IOException("无法读取该文件")
                }
                suppressWatcher = true
                binding.editor.setText(text)
                suppressWatcher = false
                binding.editor.setSelection(0)
                currentUri = uri
                currentName = queryName(uri)
                isDirty = false
                updateTitle()
                updateStatus()
            } catch (e: Exception) {
                toast(getString(R.string.err_open, e.message ?: ""))
            }
        }
    }

    private fun save() {
        val uri = currentUri
        if (uri == null) saveAs() else saveToUri(uri)
    }

    private fun saveAs() {
        saveAsLauncher.launch(currentName ?: getString(R.string.default_file_name))
    }

    private fun saveToUri(uri: Uri) {
        val content = binding.editor.text?.toString().orEmpty()
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val out = openOutput(uri) ?: throw IOException("无法写入该文件")
                    out.use {
                        it.write(TextCodec.encode(content))
                        it.flush()
                    }
                }
                currentUri = uri
                currentName = queryName(uri)
                isDirty = false
                updateTitle()
                toast(getString(R.string.saved))
                pendingAction?.also { pendingAction = null }?.invoke()
            } catch (e: Exception) {
                pendingAction = null
                toast(getString(R.string.err_save, e.message ?: ""))
            }
        }
    }

    private fun openOutput(uri: Uri) = try {
        contentResolver.openOutputStream(uri, "wt")
    } catch (e: Exception) {
        contentResolver.openOutputStream(uri, "w")
    }

    private fun queryName(uri: Uri): String {
        if ("content" == uri.scheme) {
            try {
                contentResolver.query(
                    uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
                )?.use { c ->
                    if (c.moveToFirst()) {
                        val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (i >= 0) c.getString(i)?.let { return it }
                    }
                }
            } catch (_: Exception) {
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/')
            ?: getString(R.string.untitled)
    }

    private fun takePersistable(uri: Uri) {
        if ("content" != uri.scheme) return
        for (flag in intArrayOf(
            Intent.FLAG_GRANT_READ_URI_PERMISSION,
            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )) {
            try {
                contentResolver.takePersistableUriPermission(uri, flag)
            } catch (_: Exception) {
            }
        }
    }

    private fun restoreState(state: Bundle) {
        suppressWatcher = true
        binding.editor.setText(state.getString(KEY_TEXT).orEmpty())
        suppressWatcher = false
        currentUri = state.getString(KEY_URI)?.let(Uri::parse)
        currentName = state.getString(KEY_NAME)
        isDirty = state.getBoolean(KEY_DIRTY, false)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.action_wrap)?.isChecked = prefs.wrap
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_new -> { askSaveThen { newFile() }; return true }
            R.id.action_open -> { openLauncher.launch(arrayOf("text/plain", "text/*")); return true }
            R.id.action_save -> { save(); return true }
            R.id.action_save_as -> { saveAs(); return true }
            R.id.action_find -> { showFindReplace(); return true }
            R.id.action_wrap -> {
                item.isChecked = !item.isChecked
                prefs.wrap = item.isChecked
                applyWrap(item.isChecked)
                return true
            }
            R.id.action_font_up -> { changeFontSize(+1); return true }
            R.id.action_font_down -> { changeFontSize(-1); return true }
            R.id.action_default -> { openDefaultAppsSettings(); return true }
            R.id.action_about -> { showAbout(); return true }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun newFile() {
        suppressWatcher = true
        binding.editor.setText("")
        suppressWatcher = false
        currentUri = null
        currentName = null
        isDirty = false
        updateTitle()
        updateStatus()
    }

    private fun changeFontSize(delta: Int) {
        val size = (prefs.fontSize + delta).coerceIn(10, 40)
        if (size == prefs.fontSize) return
        prefs.fontSize = size
        binding.editor.setTextSize(TypedValue.COMPLEX_UNIT_SP, size.toFloat())
        toast(getString(R.string.font_size_toast, size))
    }

    private fun askSaveThen(action: () -> Unit) {
        if (!isDirty) { action(); return }
        AlertDialog.Builder(this)
            .setTitle(R.string.unsaved_title)
            .setMessage(R.string.unsaved_message)
            .setPositiveButton(R.string.save) { _, _ ->
                pendingAction = action
                save()
            }
            .setNegativeButton(R.string.discard) { _, _ -> action() }
            .setNeutralButton(R.string.cancel, null)
            .show()
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
        } catch (e: Exception) {
            toast(getString(R.string.no_settings))
        }
    }

    private fun showAbout() {
        AlertDialog.Builder(this)
            .setTitle(R.string.app_name)
            .setMessage(R.string.about_message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun showFindReplace() {
        val b = DialogFindReplaceBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.menu_find)
            .setView(b.root)
            .setNegativeButton(R.string.close, null)
            .create()

        b.btnFindNext.setOnClickListener { findNext(b.etFind.text.toString()) }
        b.btnReplace.setOnClickListener {
            replaceOne(b.etFind.text.toString(), b.etReplace.text.toString())
        }
        b.btnReplaceAll.setOnClickListener {
            replaceAll(b.etFind.text.toString(), b.etReplace.text.toString())
        }
        dialog.show()
    }

    private fun findNext(query: String) {
        if (query.isEmpty()) return
        val editor = binding.editor
        val text = editor.text.toString()
        val from = editor.selectionEnd.coerceAtLeast(0)
        var index = text.indexOf(query, from, ignoreCase = true)
        if (index < 0) index = text.indexOf(query, 0, ignoreCase = true)
        if (index < 0) { toast(getString(R.string.not_found)); return }
        editor.requestFocus()
        editor.setSelection(index, index + query.length)
    }

    private fun replaceOne(query: String, replacement: String) {
        if (query.isEmpty()) return
        val editor = binding.editor
        val editable = editor.text
        val s = editor.selectionStart
        val e = editor.selectionEnd
        if (s in 0 until e && editable.substring(s, e).equals(query, true)) {
            editable.replace(s, e, replacement)
        }
        findNext(query)
    }

    private fun replaceAll(query: String, replacement: String) {
        if (query.isEmpty()) return
        val editor = binding.editor
        val text = editor.text.toString()
        if (text.isEmpty()) return

        val sb = StringBuilder(text.length)
        var i = 0
        var count = 0
        while (i < text.length) {
            if (i + query.length <= text.length &&
                text.regionMatches(i, query, 0, query.length, ignoreCase = true)
            ) {
                sb.append(replacement)
                i += query.length
                count++
            } else {
                sb.append(text[i])
                i++
            }
        }
        if (count == 0) { toast(getString(R.string.not_found)); return }

        val caret = editor.selectionStart.coerceAtLeast(0)
        editor.setText(sb.toString())
        editor.setSelection(caret.coerceAtMost(editor.text.length))
        toast(getString(R.string.replaced_n, count))
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    private companion object {
        const val KEY_TEXT = "state_text"
        const val KEY_URI = "state_uri"
        const val KEY_NAME = "state_name"
        const val KEY_DIRTY = "state_dirty"
    }
}