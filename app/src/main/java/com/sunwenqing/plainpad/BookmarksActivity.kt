package com.sunwenqing.plainpad

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.WindowCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sunwenqing.plainpad.databinding.ActivityBookmarksBinding
import com.sunwenqing.plainpad.databinding.ItemBookmarkBinding

/**
 * 书签管理页。
 *
 * 进入时由 MainActivity 传入行号/预览/偏移；
 * 退出时通过 [EXTRA_REMAINING_OFFSETS] 回传删除后的书签列表，
 * 若用户点了某一条，则同时回传 [EXTRA_JUMP_OFFSET] 让主界面跳过去。
 */
class BookmarksActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_FILE_NAME = "file_name"
        const val EXTRA_LINES = "lines"
        const val EXTRA_PREVIEWS = "previews"
        const val EXTRA_OFFSETS = "offsets"

        const val EXTRA_REMAINING_OFFSETS = "remaining_offsets"
        const val EXTRA_JUMP_OFFSET = "jump_offset"
    }

    data class Row(val line: Int, val preview: String, val offset: Int)

    private lateinit var binding: ActivityBookmarksBinding
    private lateinit var prefs: Prefs
    private val items = mutableListOf<Row>()
    private var jumpOffset = -1

    private val adapter = BookmarkAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        prefs = Prefs(this)
        ThemeColorHelper.apply(this, prefs.themeColor)
        AppCompatDelegate.setDefaultNightMode(prefs.themeMode)
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityBookmarksBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.statusBarFill.setStatusBarHeight()
        ThemeColorHelper.applySystemBars(this)

        readIntent()
        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter

        binding.btnBack.setOnClickListener { finish() }
        binding.btnClear.setOnClickListener { confirmClearAll() }

        render()
    }

    private fun readIntent() {
        val lines = intent.getIntArrayExtra(EXTRA_LINES) ?: intArrayOf()
        val previews = intent.getStringArrayExtra(EXTRA_PREVIEWS) ?: arrayOf()
        val offsets = intent.getIntArrayExtra(EXTRA_OFFSETS) ?: intArrayOf()

        items.clear()
        for (i in offsets.indices) {
            items.add(
                Row(
                    line = lines.getOrElse(i) { 0 },
                    preview = previews.getOrElse(i) { "" },
                    offset = offsets[i]
                )
            )
        }
    }

    private fun render() {
        val empty = items.isEmpty()
        binding.recycler.visibility = if (empty) View.GONE else View.VISIBLE
        binding.emptyState.visibility = if (empty) View.VISIBLE else View.GONE
        binding.btnClear.isEnabled = !empty

        val fileName = intent.getStringExtra(EXTRA_FILE_NAME).orEmpty()
        val count = getString(R.string.bookmark_count, items.size)
        binding.tvSubtitle.text =
            if (fileName.isEmpty()) count else getString(R.string.bookmark_subtitle, fileName, count)
        binding.tvTitle.text = getString(R.string.menu_bookmarks)
    }

    private fun confirmClearAll() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.bookmark_clear_all)
            .setMessage(R.string.bookmark_clear_confirm)
            .setPositiveButton(R.string.ok) { _, _ ->
                items.clear()
                adapter.notifyDataSetChanged()
                render()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun deleteAt(position: Int) {
        if (position !in items.indices) return
        items.removeAt(position)
        adapter.notifyItemRemoved(position)
        adapter.notifyItemRangeChanged(position, items.size - position)
        render()
    }

    private fun jumpTo(row: Row) {
        jumpOffset = row.offset
        finish()
    }

    /** 无论是点返回还是点条目，都把最新的书签列表回传给主界面 */
    override fun finish() {
        val data = Intent().apply {
            putExtra(EXTRA_REMAINING_OFFSETS, items.map { it.offset }.toIntArray())
            putExtra(EXTRA_JUMP_OFFSET, jumpOffset)
        }
        setResult(RESULT_OK, data)
        super.finish()
    }

    private inner class BookmarkAdapter : RecyclerView.Adapter<BookmarkAdapter.VH>() {

        inner class VH(val b: ItemBookmarkBinding) : RecyclerView.ViewHolder(b.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val b = ItemBookmarkBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return VH(b)
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val row = items[position]
            holder.b.tvLineNumber.text = row.line.toString()
            holder.b.tvPreview.text = row.preview.ifEmpty { getString(R.string.bookmark_no_preview) }

            holder.b.root.setOnClickListener { jumpTo(row) }
            holder.b.btnDelete.setOnClickListener {
                // 用 adapterPosition 而不是闭包里的 position，避免删除后错位
                val pos = holder.bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) deleteAt(pos)
            }
        }
    }
}
