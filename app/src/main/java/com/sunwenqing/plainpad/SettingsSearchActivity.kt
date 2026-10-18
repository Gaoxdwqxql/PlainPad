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
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import com.sunwenqing.plainpad.databinding.ActivitySettingsSearchBinding
import com.sunwenqing.plainpad.databinding.ItemSettingsSearchBinding

class SettingsSearchActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsSearchBinding
    private lateinit var prefs: Prefs
    private lateinit var adapter: SearchResultAdapter

    private var highlightColor: Int = 0

    private data class Result(
        val item: SettingsItem,
        val title: String,
        val desc: String,
        val titleMatches: List<Int>?,
        val descMatches: List<Int>?
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        prefs = Prefs(this)
        AppCompatDelegate.setDefaultNightMode(prefs.themeMode)
        super.onCreate(savedInstanceState)

        binding = ActivitySettingsSearchBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 高亮背景色：主题的 primaryContainer
        highlightColor = MaterialColors.getColor(
            binding.root, com.google.android.material.R.attr.colorPrimaryContainer
        )

        binding.toolbar.setNavigationOnClickListener { finish() }

        adapter = SearchResultAdapter { key ->
            setResult(RESULT_OK, Intent().putExtra(EXTRA_KEY, key))
            finish()
        }
        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter

        binding.etSearch.doAfterTextChanged { text ->
            val q = text?.toString().orEmpty()
            binding.btnClear.visibility = if (q.isEmpty()) View.GONE else View.VISIBLE
            doSearch(q)
        }

        binding.btnClear.setOnClickListener {
            binding.etSearch.setText("")
        }

        // 自动聚焦并弹出键盘
        binding.etSearch.requestFocus()
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)

        updateEmptyState(0, isInitial = true)
    }

    // ---------------- 搜索 ----------------

    private fun doSearch(query: String) {
        val q = query.trim()
        if (q.isEmpty()) {
            adapter.submit(emptyList())
            updateEmptyState(0, isInitial = true)
            return
        }

        val results = SettingsCatalog.ALL.mapNotNull { item ->
            val title = getString(item.titleRes)
            val desc = if (item.descRes != 0) getString(item.descRes) else ""

            val titleMatches = FuzzyMatcher.match(title, q)
            val descMatches = if (desc.isNotEmpty()) FuzzyMatcher.match(desc, q) else null

            if (titleMatches != null || descMatches != null) {
                Result(item, title, desc, titleMatches, descMatches)
            } else null
        }

        adapter.submit(results)
        updateEmptyState(results.size, isInitial = false)
    }

    private fun updateEmptyState(count: Int, isInitial: Boolean) {
        when {
            isInitial -> {
                binding.tvEmpty.text = getString(R.string.settings_search_empty_hint)
                binding.tvEmpty.visibility = View.VISIBLE
                binding.recycler.visibility = View.GONE
            }
            count == 0 -> {
                binding.tvEmpty.text = getString(R.string.settings_search_no_result)
                binding.tvEmpty.visibility = View.VISIBLE
                binding.recycler.visibility = View.GONE
            }
            else -> {
                binding.tvEmpty.visibility = View.GONE
                binding.recycler.visibility = View.VISIBLE
            }
        }
    }

    // ---------------- 高亮 ----------------

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

    // ---------------- Adapter ----------------

    private inner class SearchResultAdapter(
        private val onClick: (String) -> Unit
    ) : RecyclerView.Adapter<SearchResultAdapter.VH>() {

        private val data = mutableListOf<Result>()

        fun submit(list: List<Result>) {
            data.clear()
            data.addAll(list)
            notifyDataSetChanged()
        }

        inner class VH(val b: ItemSettingsSearchBinding) : RecyclerView.ViewHolder(b.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val b = ItemSettingsSearchBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return VH(b)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val r = data[position]
            holder.b.tvTitle.text = highlight(r.title, r.titleMatches)

            if (r.desc.isEmpty()) {
                holder.b.tvDesc.visibility = View.GONE
            } else {
                holder.b.tvDesc.visibility = View.VISIBLE
                holder.b.tvDesc.text = highlight(r.desc, r.descMatches)
            }

            holder.b.tvSection.text = getString(
                R.string.settings_search_section_prefix,
                getString(SettingsCatalog.sectionNameRes(r.item.section))
            )

            holder.b.root.setOnClickListener { onClick(r.item.key) }
        }

        override fun getItemCount(): Int = data.size
    }

    companion object {
        const val EXTRA_KEY = "settings_key"
    }
}