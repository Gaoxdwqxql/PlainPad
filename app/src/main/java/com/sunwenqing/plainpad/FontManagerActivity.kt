package com.sunwenqing.plainpad

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sunwenqing.plainpad.databinding.ActivityFontManagerBinding
import com.sunwenqing.plainpad.databinding.ItemFontBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FontManagerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFontManagerBinding
    private lateinit var prefs: Prefs
    private lateinit var adapter: FontAdapter

    private val items = mutableListOf<FontItem>()
    private var selectionMode = false
    private val selected = mutableSetOf<String>()

    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) importFont(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        prefs = Prefs(this)
        ThemeColorHelper.apply(this, prefs.themeColor)
        AppCompatDelegate.setDefaultNightMode(prefs.themeMode)
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityFontManagerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.statusBarFill.setStatusBarHeight()
        ThemeColorHelper.applySystemBars(this)

        FontManager.migrateLegacy(this, prefs)

        binding.btnBack.setOnClickListener {
            if (selectionMode) setSelectionMode(false) else finish()
        }
        binding.btnImport.setOnClickListener { importLauncher.launch("*/*") }
        binding.btnSelect.setOnClickListener { setSelectionMode(!selectionMode) }
        binding.btnSelectAll.setOnClickListener { toggleSelectAll() }
        binding.btnDelete.setOnClickListener { confirmDelete() }

        adapter = FontAdapter(
            prefs = prefs,
            onClick = { item -> onItemClick(item) },
            onLongClick = { item -> onItemLongClick(item) },
            onChecked = { item, checked -> onItemChecked(item, checked) }
        )
        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (selectionMode) setSelectionMode(false) else finish()
            }
        })

        reload()
    }

    private fun reload() {
        items.clear()

        EditorThemes.FONTS.forEach { (family, nameRes) ->
            items.add(FontItem.Builtin(family, getString(nameRes)))
        }
        FontManager.list(prefs).forEach { items.add(FontItem.Custom(it)) }

        adapter.submit(items, selectionMode, selected.toSet(), prefs.editorFontFamily)

        binding.tvEmpty.visibility =
            if (items.none { it is FontItem.Custom }) View.VISIBLE else View.GONE

        updateBottomBar()
    }

    private fun setSelectionMode(enabled: Boolean) {
        selectionMode = enabled
        if (!enabled) selected.clear()

        binding.btnSelect.text =
            getString(if (enabled) R.string.font_manage_done else R.string.font_manage)
        binding.bottomBar.visibility = if (enabled) View.VISIBLE else View.GONE
        reload()
    }

    private fun enterSelectionModeWith(item: FontItem.Custom) {
        selectionMode = true
        selected.clear()
        selected.add(item.key)
        binding.btnSelect.text = getString(R.string.font_manage_done)
        binding.bottomBar.visibility = View.VISIBLE
        reload()
    }

    private fun onItemClick(item: FontItem) {
        if (selectionMode) {
            if (item is FontItem.Custom) {
                if (selected.contains(item.key)) selected.remove(item.key)
                else selected.add(item.key)
                adapter.submit(items, true, selected.toSet(), prefs.editorFontFamily)
                updateBottomBar()
            }
            return
        }

        if (prefs.editorFontFamily == item.key) {
            finish()
            return
        }

        prefs.editorFontFamily = item.key
        finish()
    }

    private fun onItemLongClick(item: FontItem) {
        if (!selectionMode && item is FontItem.Custom) {
            enterSelectionModeWith(item)
        }
    }

    private fun onItemChecked(item: FontItem, checked: Boolean) {
        if (item !is FontItem.Custom) return
        if (checked) selected.add(item.key) else selected.remove(item.key)
        updateBottomBar()
    }

    private fun toggleSelectAll() {
        val customs = items.filterIsInstance<FontItem.Custom>()
        if (customs.isEmpty()) return

        if (selected.containsAll(customs.map { it.key })) {
            selected.clear()
        } else {
            selected.clear()
            selected.addAll(customs.map { it.key })
        }

        adapter.submit(items, selectionMode, selected.toSet(), prefs.editorFontFamily)
        updateBottomBar()
    }

    private fun updateBottomBar() {
        val customs = items.filterIsInstance<FontItem.Custom>()

        binding.btnDelete.isEnabled = selected.isNotEmpty()
        binding.btnSelectAll.isEnabled = customs.isNotEmpty()

        binding.btnDelete.text = if (selected.isEmpty()) {
            getString(R.string.font_delete)
        } else {
            getString(R.string.font_delete_n, selected.size)
        }

        val allSelected = customs.isNotEmpty() && selected.containsAll(customs.map { it.key })
        binding.btnSelectAll.text = getString(
            if (allSelected) R.string.font_unselect_all else R.string.font_select_all
        )
    }

    private fun confirmDelete() {
        val toDelete = items.filterIsInstance<FontItem.Custom>()
            .filter { selected.contains(it.key) }
        if (toDelete.isEmpty()) return

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.font_delete_confirm_title)
            .setMessage(getString(R.string.font_delete_confirm_message, toDelete.size))
            .setPositiveButton(R.string.font_delete) { _, _ ->
                val currentDeleted =
                    toDelete.any { prefs.editorFontFamily == it.key }

                val count = FontManager.delete(prefs, toDelete.map { it.font })

                setSelectionMode(false)

                Toast.makeText(
                    this,
                    getString(R.string.font_deleted_n, count),
                    Toast.LENGTH_SHORT
                ).show()

                if (currentDeleted) {
                    Toast.makeText(
                        this,
                        R.string.font_delete_current_fallback,
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun importFont(uri: Uri) {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                FontManager.importFont(this@FontManagerActivity, prefs, uri)
            }

            result.onSuccess { font ->
                prefs.editorFontFamily = FontManager.key(font)
                setSelectionMode(false)
                Toast.makeText(
                    this@FontManagerActivity,
                    R.string.import_font_success,
                    Toast.LENGTH_SHORT
                ).show()
            }.onFailure { e ->
                Toast.makeText(
                    this@FontManagerActivity,
                    getString(R.string.font_import_fail_reason, e.message ?: ""),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    // ---------------- 数据模型 ----------------

    sealed class FontItem {
        abstract val key: String
        abstract val displayName: String

        data class Builtin(
            val family: String,
            override val displayName: String
        ) : FontItem() {
            override val key: String get() = family
        }

        data class Custom(
            val font: CustomFont
        ) : FontItem() {
            override val key: String get() = FontManager.key(font)
            override val displayName: String get() = font.displayName
        }
    }

    // ---------------- Adapter ----------------

    private class FontAdapter(
        private val prefs: Prefs,
        private val onClick: (FontItem) -> Unit,
        private val onLongClick: (FontItem) -> Unit,
        private val onChecked: (FontItem, Boolean) -> Unit
    ) : RecyclerView.Adapter<FontAdapter.VH>() {

        private var items: List<FontItem> = emptyList()
        private var selectionMode = false
        private var selected: Set<String> = emptySet()
        private var currentKey: String = ""

        fun submit(
            newItems: List<FontItem>,
            mode: Boolean,
            selectedKeys: Set<String>,
            current: String
        ) {
            items = newItems.toList()
            selectionMode = mode
            selected = selectedKeys
            currentKey = current
            notifyDataSetChanged()
        }

        inner class VH(val b: ItemFontBinding) : RecyclerView.ViewHolder(b.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val b = ItemFontBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return VH(b)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            val ctx = holder.b.root.context

            holder.b.tvName.text = item.displayName
            holder.b.tvPreview.typeface = FontHelper.typefaceForKey(prefs, item.key)

            holder.b.tvTag.text = when (item) {
                is FontItem.Builtin -> ctx.getString(R.string.font_builtin)
                is FontItem.Custom -> ctx.getString(R.string.font_custom_tag)
            }

            holder.b.ivCurrent.visibility =
                if (item.key == currentKey) View.VISIBLE else View.INVISIBLE

            val canSelect = selectionMode && item is FontItem.Custom
            holder.b.cbSelect.visibility = if (canSelect) View.VISIBLE else View.GONE
            holder.b.cbSelect.setOnCheckedChangeListener(null)
            holder.b.cbSelect.isChecked = selected.contains(item.key)
            holder.b.cbSelect.setOnCheckedChangeListener { _, checked ->
                onChecked(item, checked)
            }

            holder.b.root.alpha =
                if (selectionMode && item is FontItem.Builtin) 0.5f else 1f

            holder.b.root.setOnClickListener { onClick(item) }
            holder.b.root.setOnLongClickListener {
                onLongClick(item)
                true
            }
        }

        override fun getItemCount(): Int = items.size
    }
}