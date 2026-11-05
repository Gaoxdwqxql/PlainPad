package com.sunwenqing.plainpad

import android.content.Intent
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.WindowCompat
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import com.sunwenqing.plainpad.databinding.ActivityHelpSearchBinding
import com.sunwenqing.plainpad.databinding.ItemHelpSearchBinding

/**
 * 帮助页搜索。仿设置页搜索：输入关键词后列出命中的条目，
 * 每条显示标题 + 命中处上下文片段，点击把 section id 回传给帮助页跳转。
 */
class HelpSearchActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SECTION_ID = "help_section_id"
    }

    private lateinit var binding: ActivityHelpSearchBinding
    private lateinit var prefs: Prefs
    private lateinit var adapter: ResultAdapter
    private var highlightColor: Int = 0

    private data class Result(
        val section: HelpSection,
        val title: String,
        val snippet: String,
        val titleMatches: List<Int>?,
        val snippetMatches: List<Int>?
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        prefs = Prefs(this)
        ThemeColorHelper.apply(this, prefs.themeColor)
        AppCompatDelegate.setDefaultNightMode(prefs.themeMode)
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityHelpSearchBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.statusBarFill.setStatusBarHeight()
        ThemeColorHelper.applySystemBars(this)

        highlightColor = MaterialColors.getColor(
            binding.root, com.google.android.material.R.attr.colorPrimaryContainer
        )

        binding.toolbar.setNavigationOnClickListener { finish() }

        adapter = ResultAdapter { sectionId ->
            setResult(RESULT_OK, Intent().putExtra(EXTRA_SECTION_ID, sectionId))
            finish()
        }
        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter

        binding.etSearch.doAfterTextChanged { text ->
            val q = text?.toString().orEmpty()
            binding.btnClear.visibility = if (q.isEmpty()) View.GONE else View.VISIBLE
            doSearch(q)
        }
        binding.btnClear.setOnClickListener { binding.etSearch.setText("") }

        binding.etSearch.requestFocus()
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)

        updateEmptyState(0, initial = true)
    }

    private fun doSearch(query: String) {
        val q = query.trim()
        if (q.isEmpty()) {
            adapter.submit(emptyList())
            updateEmptyState(0, initial = true)
            return
        }

        val results = HelpCatalog.ALL.mapNotNull { section ->
            val title = getString(section.titleRes)
            val content = getString(section.contentRes)

            val titleMatches = FuzzyMatcher.match(title, q)
            val contentMatches = FuzzyMatcher.match(content, q)
            if (titleMatches == null && contentMatches == null) return@mapNotNull null

            val anchor = contentMatches?.firstOrNull() ?: 0
            val start = (anchor - 30).coerceAtLeast(0)
            val end = (anchor + 80).coerceAtMost(content.length)
            val raw = content.substring(start, end).replace('\n', ' ').trim()
            val snippet = buildString {
                if (start > 0) append('…')
                append(raw)
                if (end < content.length) append('…')
            }
            // 片段是截断过的，命中位置要跟着平移
            val snippetMatches = contentMatches
                ?.filter { it in start until end }
                ?.map { it - start }

            Result(section, title, snippet, titleMatches, snippetMatches)
        }

        adapter.submit(results)
        updateEmptyState(results.size, initial = false)
    }

    private fun updateEmptyState(count: Int, initial: Boolean) {
        when {
            initial -> {
                binding.tvEmpty.text = getString(R.string.help_search_initial)
                binding.tvEmpty.visibility = View.VISIBLE
                binding.recycler.visibility = View.GONE
            }
            count == 0 -> {
                binding.tvEmpty.text = getString(R.string.help_search_empty)
                binding.tvEmpty.visibility = View.VISIBLE
                binding.recycler.visibility = View.GONE
            }
            else -> {
                binding.tvEmpty.visibility = View.GONE
                binding.recycler.visibility = View.VISIBLE
            }
        }
    }

    private fun highlight(text: String, positions: List<Int>?): CharSequence {
        if (positions.isNullOrEmpty()) return text
        val sp = SpannableString(text)
        for (i in positions) {
            if (i in text.indices) {
                sp.setSpan(
                    BackgroundColorSpan(highlightColor),
                    i, i + 1,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }
        return sp
    }

    private inner class ResultAdapter(
        private val onClick: (String) -> Unit
    ) : RecyclerView.Adapter<ResultAdapter.VH>() {

        private val data = mutableListOf<Result>()

        fun submit(list: List<Result>) {
            data.clear()
            data.addAll(list)
            notifyDataSetChanged()
        }

        inner class VH(val b: ItemHelpSearchBinding) : RecyclerView.ViewHolder(b.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val b = ItemHelpSearchBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return VH(b)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val r = data[position]
            holder.b.tvTitle.text = highlight(r.title, r.titleMatches)
            holder.b.tvSnippet.text = highlight(r.snippet, r.snippetMatches)
            holder.b.root.setOnClickListener { onClick(r.section.id) }
        }

        override fun getItemCount(): Int = data.size
    }
}
