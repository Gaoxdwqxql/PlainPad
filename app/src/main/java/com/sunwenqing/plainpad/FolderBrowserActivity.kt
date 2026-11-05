package com.sunwenqing.plainpad

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.WindowCompat
import com.sunwenqing.plainpad.databinding.ActivityFolderBrowserBinding
import com.sunwenqing.plainpad.databinding.ItemFolderEntryBinding
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 用户添加过的一个文件夹（SAF 树根） */
data class FolderRoot(val uri: Uri, val name: String)

/** 文件夹清单的持久化：金额很小，JSON 足够 */
object FolderStore {

    fun load(prefs: Prefs): List<FolderRoot> {
        val raw = prefs.foldersJson
        if (raw.isBlank()) return emptyList()
        return try {
            val array = JSONArray(raw)
            val list = ArrayList<FolderRoot>(array.length())
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val uri = obj.optString("uri")
                if (uri.isEmpty()) continue
                list.add(FolderRoot(Uri.parse(uri), obj.optString("name")))
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun save(prefs: Prefs, list: List<FolderRoot>) {
        val array = JSONArray()
        list.forEach { root ->
            array.put(JSONObject().apply {
                put("uri", root.uri.toString())
                put("name", root.name)
            })
        }
        prefs.foldersJson = array.toString()
    }
}

/**
 * 文件夹浏览。两种状态：
 * 1) 文件夹列表：展示添加过的文件夹，可进入或移除
 * 2) 浏览某文件夹：展示子目录与文本文件
 *
 * 只读取文件，不会修改任何内容；点文件才回到编辑器打开。
 */
class FolderBrowserActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_URI = "extra_uri"
        private const val REQ_TREE = 1001
    }

    private data class Entry(
        val uri: Uri,
        val name: String,
        val isDir: Boolean,
        val meta: String,
        val action: String?
    )

    private lateinit var binding: ActivityFolderBrowserBinding
    private lateinit var prefs: Prefs

    private val roots = mutableListOf<FolderRoot>()
    private var browsingRoot: FolderRoot? = null
    private val stack = ArrayDeque<Uri>()
    private var entries: List<Entry> = emptyList()
    private var shown: List<Entry> = emptyList()
    private var searchQuery: String = ""

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        prefs = Prefs(this)
        ThemeColorHelper.apply(this, prefs.themeColor)
        AppCompatDelegate.setDefaultNightMode(prefs.themeMode)
        super.onCreate(savedInstanceState)

        binding = ActivityFolderBrowserBinding.inflate(layoutInflater)
        setContentView(binding.root)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        ThemeColorHelper.applySystemBars(this)

        binding.btnFolderClose.setOnClickListener { finish() }
        binding.btnFolderUp.setOnClickListener { goUp() }
        binding.btnFolderRefresh.setOnClickListener { refreshCurrent() }
        binding.btnPickFolder.setOnClickListener { pickFolder() }
        setupSearch()

        binding.listFolder.adapter = object : BaseAdapter() {
            override fun getCount() = shown.size
            override fun getItem(position: Int) = shown[position]
            override fun getItemId(position: Int) = position.toLong()

            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val item = if (convertView == null) {
                    ItemFolderEntryBinding.inflate(layoutInflater, parent, false)
                } else {
                    ItemFolderEntryBinding.bind(convertView)
                }
                val entry = shown[position]
                item.tvEntryName.text = if (entry.isDir) "▸ ${entry.name}" else entry.name
                item.tvEntryMeta.text = entry.meta

                if (entry.action == null) {
                    item.tvEntryAction.visibility = View.GONE
                } else {
                    item.tvEntryAction.visibility = View.VISIBLE
                    item.tvEntryAction.text = entry.action
                    item.tvEntryAction.setOnClickListener { removeRoot(entry.uri) }
                }
                return item.root
            }
        }

        binding.listFolder.setOnItemClickListener { _, _, position, _ ->
            val entry = shown.getOrNull(position) ?: return@setOnItemClickListener
            if (browsingRoot == null) {
                enterRoot(entry.uri)
            } else if (entry.isDir) {
                stack.addLast(entry.uri)
                refresh(entry.uri)
            } else {
                setResult(RESULT_OK, Intent().putExtra(EXTRA_URI, entry.uri))
                finish()
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    browsingRoot == null -> finish()
                    stack.size > 1 -> goUp()
                    else -> showRoots()
                }
            }
        })

        roots.clear()
        roots.addAll(FolderStore.load(prefs))
        showRoots()
    }

    // ---------------- 文件夹列表 ----------------

    private fun showRoots() {
        browsingRoot = null
        stack.clear()

        binding.tvFolderTitle.setText(R.string.folder_title)
        binding.tvFolderPath.text = getString(R.string.folder_roots_hint)
        binding.btnFolderUp.visibility = View.GONE
        binding.btnFolderRefresh.visibility = View.GONE
        binding.btnPickFolder.setText(R.string.folder_add)

        entries = roots.map { root ->
            Entry(root.uri, root.name, true, getString(R.string.folder_root_meta), getString(R.string.folder_remove))
        }
        shown = if (searchQuery.isBlank()) entries
        else entries.filter { it.name.contains(searchQuery, ignoreCase = true) }
        render(if (entries.isEmpty()) R.string.folder_roots_empty else 0)
    }

    private fun enterRoot(uri: Uri) {
        val root = roots.firstOrNull { it.uri == uri } ?: return
        if (!hasPermission(uri)) {
            AppTips.showLong(binding.root, R.string.folder_permission_lost)
            removeRoot(uri)
            return
        }
        browsingRoot = root
        stack.clear()
        stack.addLast(uri)
        refresh(uri)
    }

    private fun removeRoot(uri: Uri) {
        roots.removeAll { it.uri == uri }
        FolderStore.save(prefs, roots)
        try {
            contentResolver.releasePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: Exception) {
            // 释放失败不影响本地清单
        }
        showRoots()
    }

    // ---------------- 目录内容 ----------------

    private fun refreshCurrent() {
        stack.lastOrNull()?.let { refresh(it) }
    }

    private fun goUp() {
        if (stack.size > 1) {
            stack.removeLast()
            refresh(stack.last())
        } else {
            showRoots()
        }
    }

    private fun refresh(dirUri: Uri) {
        val root = browsingRoot ?: return
        val list = ArrayList<Entry>()

        binding.tvFolderTitle.text = root.name
        binding.btnFolderUp.visibility = View.VISIBLE
        binding.btnFolderRefresh.visibility = View.VISIBLE
        binding.btnPickFolder.setText(R.string.folder_add)

        try {
            val docId = if (DocumentsContract.isTreeUri(dirUri)) {
                DocumentsContract.getTreeDocumentId(dirUri)
            } else {
                DocumentsContract.getDocumentId(dirUri)
            }
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(root.uri, docId)
            contentResolver.query(
                children,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                    DocumentsContract.Document.COLUMN_SIZE,
                    DocumentsContract.Document.COLUMN_LAST_MODIFIED
                ),
                null, null, null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getString(0) ?: continue
                    val name = cursor.getString(1) ?: continue
                    val mime = cursor.getString(2).orEmpty()
                    val isDir = mime == DocumentsContract.Document.MIME_TYPE_DIR
                    if (!isDir && !isTextLike(name, mime)) continue

                    val size = if (cursor.isNull(3)) 0L else cursor.getLong(3)
                    val modified = if (cursor.isNull(4)) 0L else cursor.getLong(4)
                    val meta = if (isDir) {
                        getString(R.string.folder_dir)
                    } else {
                        listOf(formatSize(size), formatDate(modified))
                            .filter { it.isNotEmpty() }
                            .joinToString(" · ")
                    }

                    list.add(
                        Entry(
                            DocumentsContract.buildDocumentUriUsingTree(root.uri, id),
                            name, isDir, meta, null
                        )
                    )
                }
            }
        } catch (_: Exception) {
            AppTips.showLong(binding.root, R.string.folder_permission_lost)
        }

        list.sortWith(compareBy({ !it.isDir }, { it.name.lowercase() }))
        entries = list
        shown = if (searchQuery.isBlank()) list else list.filter { it.name.contains(searchQuery, ignoreCase = true) }
        binding.tvFolderPath.text = currentPath(dirUri)
        render(if (list.isEmpty()) R.string.folder_empty else 0)
    }

    private fun setupSearch() {
        val density = resources.displayMetrics.density
        val edit = android.widget.EditText(this).apply {
            hint = getString(R.string.search_hint_short)
            setSingleLine(true)
            setBackgroundResource(R.drawable.bg_dialog_input)
            setPadding((14 * density).toInt(), (10 * density).toInt(), (14 * density).toInt(), (10 * density).toInt())
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(com.google.android.material.color.MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface))
        }
        val listParent = binding.listFolder.parent as? android.widget.FrameLayout ?: return
        (binding.root as android.widget.LinearLayout).addView(
            edit, 2,
            android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins((16 * density).toInt(), 0, (16 * density).toInt(), (8 * density).toInt()) }
        )
        edit.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                searchQuery = s?.toString().orEmpty()
                applyFilter()
            }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })
    }

    private fun applyFilter() {
        shown = if (searchQuery.isBlank()) entries
        else entries.filter { it.name.contains(searchQuery, ignoreCase = true) }
        (binding.listFolder.adapter as? BaseAdapter)?.notifyDataSetChanged()
        if (shown.isEmpty() && entries.isNotEmpty()) {
            binding.folderEmptyBox.visibility = View.VISIBLE
            binding.tvFolderEmpty.setText(R.string.search_no_result)
        }
    }

    private fun render(emptyTextRes: Int) {
        (binding.listFolder.adapter as BaseAdapter).notifyDataSetChanged()
        if (emptyTextRes != 0) {
            binding.folderEmptyBox.visibility = View.VISIBLE
            binding.tvFolderEmpty.setText(emptyTextRes)
        } else {
            binding.folderEmptyBox.visibility = View.GONE
        }
    }

    private fun currentPath(dirUri: Uri): String = try {
        val docId = if (DocumentsContract.isTreeUri(dirUri)) {
            DocumentsContract.getTreeDocumentId(dirUri)
        } else {
            DocumentsContract.getDocumentId(dirUri)
        }
        docId.substringAfter(':').ifEmpty { "/" }
    } catch (_: Exception) {
        "/"
    }

    // ---------------- 添加文件夹 ----------------

    private fun pickFolder() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            )
        }
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(intent, REQ_TREE)
        } catch (_: Exception) {
            AppTips.showLong(binding.root, R.string.folder_unavailable)
        }
    }

    @Deprecated("ACTION_OPEN_DOCUMENT_TREE 需要 startActivityForResult 才能拿持久权限")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_TREE) return

        val uri = data?.data
        if (resultCode != RESULT_OK || uri == null) return

        try {
            contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: Exception) {
            // 个别提供方不支持持久化，本次会话内仍可用
        }

        val name = rootName(uri)
        roots.removeAll { it.uri == uri }
        roots.add(FolderRoot(uri, name))
        FolderStore.save(prefs, roots)
        showRoots()
    }

    private fun rootName(uri: Uri): String = try {
        val docId = DocumentsContract.getTreeDocumentId(uri)
        docId.substringAfter(':').trimEnd('/').substringAfterLast('/').ifEmpty {
            getString(R.string.folder_title)
        }
    } catch (_: Exception) {
        getString(R.string.folder_title)
    }

    // ---------------- 工具 ----------------

    private fun hasPermission(uri: Uri): Boolean =
        contentResolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }

    private fun formatSize(bytes: Long): String = when {
        bytes <= 0 -> ""
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
        else -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
    }

    private fun formatDate(millis: Long): String =
        if (millis <= 0) "" else dateFormat.format(Date(millis))

    private fun isTextLike(name: String, mime: String): Boolean {
        if (mime.startsWith("text/")) return true
        val lower = name.lowercase()
        return lower.endsWith(".txt") || lower.endsWith(".md") || lower.endsWith(".log") ||
                lower.endsWith(".json") || lower.endsWith(".csv") || lower.endsWith(".xml") ||
                lower.endsWith(".ini") || lower.endsWith(".yml") || lower.endsWith(".yaml")
    }
}
