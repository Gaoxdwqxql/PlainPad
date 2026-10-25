package com.sunwenqing.plainpad

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.WindowCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.sunwenqing.plainpad.databinding.ActivityOnboardingBinding
import com.sunwenqing.plainpad.databinding.ItemOnboardingBinding
import com.sunwenqing.plainpad.databinding.ItemOnboardingDotBinding

class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding
    private lateinit var prefs: Prefs
    private lateinit var pages: List<Page>

    data class Page(val iconRes: Int, val title: String, val desc: String)

    override fun onCreate(savedInstanceState: Bundle?) {
        prefs = Prefs(this)
        ThemeColorHelper.apply(this, prefs.themeColor)
        AppCompatDelegate.setDefaultNightMode(prefs.themeMode)
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // ★ 设置纯色填充高度
        binding.statusBarFill.setStatusBarHeight()

        ThemeColorHelper.applySystemBars(this)

        pages = buildPages()

        binding.pager.adapter = OnboardingAdapter(pages)
        binding.pager.offscreenPageLimit = 1

        setupIndicator()
        setupButtons()

        binding.pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateIndicator(position)
                updateNextButton(position)
            }
        })

        updateIndicator(0)
        updateNextButton(0)
    }

    private fun buildPages(): List<Page> = listOf(
        Page(R.drawable.ic_onboard_welcome, getString(R.string.onboard_welcome_title), getString(R.string.onboard_welcome_desc)),
        Page(R.drawable.ic_onboard_tabs, getString(R.string.onboard_tabs_title), getString(R.string.onboard_tabs_desc)),
        Page(R.drawable.ic_onboard_save, getString(R.string.onboard_save_title), getString(R.string.onboard_save_desc)),
        Page(R.drawable.ic_onboard_find, getString(R.string.onboard_find_title), getString(R.string.onboard_find_desc)),
        Page(R.drawable.ic_onboard_appearance, getString(R.string.onboard_appearance_title), getString(R.string.onboard_appearance_desc)),
        Page(R.drawable.ic_onboard_gesture, getString(R.string.onboard_gesture_title), getString(R.string.onboard_gesture_desc)),
        Page(R.drawable.ic_onboard_special, getString(R.string.onboard_special_title), getString(R.string.onboard_special_desc)),
        Page(R.drawable.ic_onboard_done, getString(R.string.onboard_done_title), getString(R.string.onboard_done_desc))
    )

    private val dots = mutableListOf<View>()

    private fun setupIndicator() {
        binding.indicatorContainer.removeAllViews()
        dots.clear()

        repeat(pages.size) {
            val dotBinding = ItemOnboardingDotBinding.inflate(layoutInflater, binding.indicatorContainer, false)
            binding.indicatorContainer.addView(dotBinding.root)
            dots.add(dotBinding.root)
        }
    }

    private fun updateIndicator(current: Int) {
        for (i in dots.indices) {
            val alpha = if (i == current) 1f else 0.25f
            dots[i].alpha = alpha
            val scale = if (i == current) 1.2f else 1f
            dots[i].scaleX = scale
            dots[i].scaleY = scale
        }
    }

    private fun setupButtons() {
        binding.btnNext.setOnClickListener {
            val cur = binding.pager.currentItem
            if (cur < pages.size - 1) {
                binding.pager.currentItem = cur + 1
            } else {
                finishOnboarding()
            }
        }

        binding.btnPrev.setOnClickListener {
            val cur = binding.pager.currentItem
            if (cur > 0) {
                binding.pager.currentItem = cur - 1
            }
        }

        binding.btnSkipTop.setOnClickListener { finishOnboarding() }
    }

    private fun updateNextButton(position: Int) {
        val isFirst = position == 0
        val isLast = position == pages.size - 1

        binding.btnNext.text = getString(if (isLast) R.string.onboard_start else R.string.onboard_next)
        binding.btnPrev.visibility = if (isFirst) View.INVISIBLE else View.VISIBLE
        binding.btnSkipTop.visibility = if (isLast) View.INVISIBLE else View.VISIBLE
    }

    private fun finishOnboarding() {
        prefs.onboardingDone = true
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private class OnboardingAdapter(private val pages: List<Page>) : RecyclerView.Adapter<OnboardingAdapter.VH>() {
        class VH(val binding: ItemOnboardingBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val binding = ItemOnboardingBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return VH(binding)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val page = pages[position]
            holder.binding.ivIcon.setImageResource(page.iconRes)
            holder.binding.tvTitle.text = page.title
            holder.binding.tvDesc.text = page.desc
        }

        override fun getItemCount(): Int = pages.size
    }
}