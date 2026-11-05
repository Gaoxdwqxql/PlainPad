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
import com.sunwenqing.plainpad.databinding.ActivityRecentFilesBinding
import com.sunwenqing.plainpad.databinding.ItemRecentFileBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 最近文件列表页。点击条目把 uri 回传给 MainActivity 打开，
 * 支持单条移除与清空，都不再占用弹窗。
 */
class RecentFilesActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_URI = "recent_uri"
    }

    private lateinit var binding: ActivityRecentFilesBinding
    private lateinit var prefs: Prefs

    private val fmt = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
    private val adapter = RecentAdapter()
    private var searchQuery: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        prefs = Prefs(this)
        ThemeColorHelper.apply(this, prefs.themeColor)
        AppCompatDelegate.setDefaultNightMode(prefs.themeMode)
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityRecentFilesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.statusBarFill.setStatusBarHeight()
        ThemeColorHelper.applySystemBars(this)

        binding.btnBack.setOnClickListener { finish() }
        binding.btnClear.setOnClickListener { confirmClearAll() }

        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter
        setupSearch()
        attachSwipeToDelete()

        render()
    }

    /** 左滑删除：滑出错误的最近记录比先点 ✕ 再确认更快 */
    private fun attachSwipeToDelete() {
        val bg = android.graphics.drawable.ColorDrawable(
            com.google.android.material.color.MaterialColors.getColor(
                binding.root, com.google.android.material.R.attr.colorErrorContainer
            )
        )
        val callback = object : androidx.recyclerview.widget.ItemTouchHelper.SimpleCallback(
            0, androidx.recyclerview.widget.ItemTouchHelper.LEFT
        ) {
            override fun onMove(
                recyclerView: androidx.recyclerview.widget.RecyclerView,
                viewHolder: androidx.recyclerview.widget.RecyclerView.ViewHolder,
                target: androidx.recyclerview.widget.RecyclerView.ViewHolder
            ) = false

            override fun onChildDraw(
                c: android.graphics.Canvas,
                recyclerView: androidx.recyclerview.widget.RecyclerView,
                viewHolder: androidx.recyclerview.widget.RecyclerView.ViewHolder,
                dX: Float,
                dY: Float,
                actionState: Int,
                isCurrentlyActive: Boolean
            ) {
                val item = viewHolder.itemView
                bg.setBounds(item.left, item.top, item.right, item.bottom)
                bg.draw(c)
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            }

            override fun onSwiped(
                viewHolder: androidx.recyclerview.widget.RecyclerView.ViewHolder,
                direction: Int
            ) {
                val file = adapter.itemAt(viewHolder.bindingAdapterPosition) ?: return
                prefs.removeRecentFile(file.uri)
                render()
            }
        }
        androidx.recyclerview.widget.ItemTouchHelper(callback).attachToRecyclerView(binding.recycler)
    }

    private fun setupSearch() {
        val root = binding.root as? android.widget.LinearLayout ?: return
        val density = resources.displayMetrics.density
        val edit = android.widget.EditText(this).apply {
            hint = getString(R.string.search_hint_short)
            setSingleLine(true)
            setBackgroundResource(R.drawable.bg_dialog_input)
            setPadding((14 * density).toInt(), (10 * density).toInt(), (14 * density).toInt(), (10 * density).toInt())
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(com.google.android.material.color.MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface))
        }
        root.addView(
            edit, 1,
            android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins((16 * density).toInt(), 0, (16 * density).toInt(), (8 * density).toInt()) }
        )
        edit.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                searchQuery = s?.toString().orEmpty()
                render()
            }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })
    }

    private fun render() {
        val all = prefs.getRecentFiles()
        val files = if (searchQuery.isBlank()) all
        else all.filter { it.name.contains(searchQuery, ignoreCase = true) }
        adapter.submit(files)

        val empty = files.isEmpty()
        binding.recycler.visibility = if (empty) View.GONE else View.VISIBLE
        binding.tvEmpty.visibility = if (empty) View.VISIBLE else View.GONE
        binding.btnClear.isEnabled = !empty
        binding.tvSubtitle.text =
            if (empty) "" else getString(R.string.recent_count, files.size)
    }

    private fun confirmClearAll() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.recent_title)
            .setMessage(R.string.recent_clear_confirm)
            .setPositiveButton(R.string.ok) { _, _ ->
                prefs.clearRecentFiles()
                render()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private inner class RecentAdapter : RecyclerView.Adapter<RecentAdapter.VH>() {

        private val data = mutableListOf<RecentFile>()

        fun itemAt(position: Int): RecentFile? = data.getOrNull(position)

        fun submit(list: List<RecentFile>) {
            data.clear()
            data.addAll(list)
            notifyDataSetChanged()
        }

        inner class VH(val b: ItemRecentFileBinding) : RecyclerView.ViewHolder(b.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val b = ItemRecentFileBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return VH(b)
        }

        override fun getItemCount(): Int = data.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val file = data[position]
            holder.b.tvName.text = file.name
            holder.b.tvTime.text = if (file.timestamp > 0) {
                fmt.format(Date(file.timestamp))
            } else {
                ""
            }

            holder.b.root.setOnClickListener {
                setResult(RESULT_OK, Intent().putExtra(EXTRA_URI, file.uri))
                finish()
            }

            holder.b.btnRemove.setOnClickListener {
                val pos = holder.bindingAdapterPosition
                if (pos == RecyclerView.NO_POSITION) return@setOnClickListener
                prefs.removeRecentFile(data[pos].uri)
                render()
            }
        }
    }
}
