package com.sunwenqing.plainpad

import android.content.Context
import android.content.res.ColorStateList
import android.util.TypedValue
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import com.google.android.material.chip.Chip
import com.sunwenqing.plainpad.databinding.ActivityMainBinding

/**
 * 标签栏 UI 渲染器。
 * 只负责根据 documents 列表画出 Chip 与 "+" 按钮，并通过回调把用户操作返回给调用方。
 *
 * Chip 的颜色显式绑定到 colorPrimary / colorPrimaryContainer，
 * 保证切换主题色时标签栏跟随变化（Material 3 的 Chip 默认使用 colorSecondaryContainer，
 * 不随 colorPrimary 变化）。
 */
object TabBarRenderer {

    fun render(
        context: Context,
        binding: ActivityMainBinding,
        documents: List<OpenDocument>,
        currentIndex: Int,
        onTabClick: (Int) -> Unit,
        onTabClose: (Int) -> Unit,
        onAddClick: () -> Unit
    ) {
        val container = binding.tabContainer
        container.removeAllViews()

        val density = context.resources.displayMetrics.density

        // 预解析主题色
        val primaryColor = resolveAttr(context, com.google.android.material.R.attr.colorPrimary)
        val onPrimaryColor = resolveAttr(context, com.google.android.material.R.attr.colorOnPrimary)
        val primaryContainer = resolveAttr(
            context, com.google.android.material.R.attr.colorPrimaryContainer
        )
        val onPrimaryContainer = resolveAttr(
            context, com.google.android.material.R.attr.colorOnPrimaryContainer
        )
        val surfaceVariant = resolveAttr(
            context, com.google.android.material.R.attr.colorSurfaceVariant
        )
        val onSurfaceVariant = resolveAttr(
            context, com.google.android.material.R.attr.colorOnSurfaceVariant
        )
        val outline = resolveAttr(
            context, com.google.android.material.R.attr.colorOutline
        )

        documents.forEachIndexed { index, doc ->
            val checked = index == currentIndex

            val chip = Chip(context).apply {
                text = if (doc.isDirty) "${doc.displayName} *" else doc.displayName
                isCheckable = true
                isCheckedIconVisible = false
                isCloseIconVisible = true
                isChecked = checked
                setEnsureMinTouchTargetSize(false)
                chipMinHeight = density * 32

                // ★ 显式绑定主题色，不依赖 Chip 默认的 colorSecondaryContainer
                if (checked) {
                    chipBackgroundColor = ColorStateList.valueOf(primaryContainer)
                    setTextColor(onPrimaryContainer)
                    val iconColor = ColorStateList.valueOf(onPrimaryContainer)
                    closeIconTint = iconColor
                    chipStrokeWidth = 0f
                } else {
                    chipBackgroundColor = ColorStateList.valueOf(surfaceVariant)
                    setTextColor(onSurfaceVariant)
                    closeIconTint = ColorStateList.valueOf(onSurfaceVariant)
                    chipStrokeWidth = density * 1f
                    chipStrokeColor = ColorStateList.valueOf(outline)
                }

                setOnClickListener { onTabClick(index) }
                setOnCloseIconClickListener { onTabClose(index) }

                val lp = ViewGroup.MarginLayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                lp.marginEnd = (density * 4).toInt()
                layoutParams = lp
            }
            container.addView(chip)
        }

        // "+" 按钮
        val addBtn = Chip(context).apply {
            text = "+"
            isCheckable = false
            isCloseIconVisible = false
            isCheckedIconVisible = false
            setEnsureMinTouchTargetSize(false)
            chipMinHeight = density * 32

            chipBackgroundColor = ColorStateList.valueOf(surfaceVariant)
            setTextColor(onSurfaceVariant)
            chipStrokeWidth = density * 1f
            chipStrokeColor = ColorStateList.valueOf(outline)

            setOnClickListener { onAddClick() }
        }
        container.addView(addBtn)

        binding.tabScroll.post {
            val chip = container.getChildAt(currentIndex) ?: return@post
            val scrollX = chip.left - (binding.tabScroll.width - chip.width) / 2
            binding.tabScroll.smoothScrollTo(scrollX.coerceAtLeast(0), 0)
        }

        // primaryColor 只用于未使用的预留，防止 Lint 报未使用变量
        @Suppress("UNUSED_VARIABLE")
        val _unused = primaryColor to onPrimaryColor
    }

    /** 从主题解析颜色值 */
    private fun resolveAttr(context: Context, attr: Int): Int {
        val tv = TypedValue()
        return if (context.theme.resolveAttribute(attr, tv, true)) {
            if (tv.resourceId != 0) ContextCompat.getColor(context, tv.resourceId)
            else tv.data
        } else {
            0xFF888888.toInt()
        }
    }
}