package com.sunwenqing.plainpad

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.WindowCompat
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.sunwenqing.plainpad.databinding.ActivityHelpBinding
import com.sunwenqing.plainpad.databinding.ItemHelpSectionBinding

/**
 * 帮助页：条目由 [HelpCatalog] 驱动，支持逐条折叠。
 * 搜索走独立的 [HelpSearchActivity]（与设置页一致）：搜索页列出命中条目，
 * 点一条返回这里，自动展开该节、滚动到位并高亮整张卡片。
 */
class HelpActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHelpBinding
    private lateinit var prefs: Prefs

    /** 被手工折叠的条目 id */
    private val collapsed = mutableSetOf<String>()

    /** 已生成的卡片，定位时直接复用 */
    private val items = mutableListOf<Pair<ItemHelpSectionBinding, HelpSection>>()

    private val searchLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != RESULT_OK) return@registerForActivityResult
            val id = result.data?.getStringExtra(HelpSearchActivity.EXTRA_SECTION_ID)
            if (!id.isNullOrEmpty()) locateSection(id)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        prefs = Prefs(this)
        ThemeColorHelper.apply(this, prefs.themeColor)
        AppCompatDelegate.setDefaultNightMode(prefs.themeMode)
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityHelpBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // ★ 设置纯色填充高度
        binding.statusBarFill.setStatusBarHeight()

        ThemeColorHelper.applySystemBars(this)

        binding.helpToolbar.setNavigationOnClickListener { finish() }
        binding.helpToolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_search) {
                searchLauncher.launch(Intent(this, HelpSearchActivity::class.java))
                true
            } else false
        }

        buildSections()
    }

    // ---------------- 条目生成 ----------------

    private fun buildSections() {
        binding.sectionContainer.removeAllViews()
        items.clear()

        HelpCatalog.ALL.forEach { section ->
            val item = ItemHelpSectionBinding.inflate(
                layoutInflater, binding.sectionContainer, false
            )
            item.tvSectionTitle.setText(section.titleRes)
            item.tvSectionContent.setText(section.contentRes)
            item.root.tag = section.id
            applyCollapsed(item, section.id in collapsed)

            item.headerSection.setOnClickListener {
                if (section.id in collapsed) collapsed.remove(section.id)
                else collapsed.add(section.id)
                applyCollapsed(item, section.id in collapsed)
            }

            binding.sectionContainer.addView(item.root)
            items.add(item to section)
        }
    }

    private fun applyCollapsed(item: ItemHelpSectionBinding, isCollapsed: Boolean) {
        item.tvSectionContent.visibility = if (isCollapsed) View.GONE else View.VISIBLE
        item.ivSectionExpand.rotation = if (isCollapsed) 0f else 180f
    }

    // ---------------- 从搜索结果定位 ----------------

    private fun locateSection(sectionId: String) {
        val pair = items.firstOrNull { it.second.id == sectionId } ?: return
        val (item, section) = pair

        // 展开命中的这一节
        collapsed.remove(section.id)
        applyCollapsed(item, false)

        binding.helpScroll.post {
            val y = offsetInScroll(item.root)
            binding.helpScroll.smoothScrollTo(0, (y - 120).coerceAtLeast(0))
            flashCard(item.root)
        }
    }

    /** 累计到 ScrollView 内的偏移（卡片 top 只相对 sectionContainer，不含上方简介区） */
    private fun offsetInScroll(view: View): Int {
        var y = 0
        var v: View? = view
        while (v != null && v !== binding.helpScroll) {
            y += v.top
            val parent = v.parent
            v = parent as? View
        }
        return y
    }

    private fun flashCard(view: View) {
        val card = view as? MaterialCardView ?: return
        val highlight = MaterialColors.getColor(
            card, com.google.android.material.R.attr.colorPrimaryContainer
        )
        val original = card.cardBackgroundColor
        card.setCardBackgroundColor(highlight)
        card.postDelayed({ card.setCardBackgroundColor(original) }, 1400)
    }
}
