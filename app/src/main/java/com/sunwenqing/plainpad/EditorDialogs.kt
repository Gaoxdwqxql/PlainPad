package com.sunwenqing.plainpad

import android.app.Activity
import android.app.Dialog
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sunwenqing.plainpad.databinding.DialogChoiceBinding
import com.sunwenqing.plainpad.databinding.DialogChoiceItemBinding
import com.sunwenqing.plainpad.databinding.DialogEncodingBinding
import com.sunwenqing.plainpad.databinding.DialogInputBinding
import com.sunwenqing.plainpad.databinding.DialogListBinding
import com.sunwenqing.plainpad.databinding.DialogSnippetEditBinding
import com.sunwenqing.plainpad.databinding.DialogSpecialCharBinding
import com.sunwenqing.plainpad.databinding.DialogStatsBinding
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.lifecycle.LifecycleOwner

/** 所有对话框集中管理，通过回调把结果交给调用方 */
object EditorDialogs {

    // ---------------- 关于 ----------------

    fun showAbout(activity: Activity) {
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.app_name)
            .setMessage(R.string.about_message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    // ---------------- 未保存提示 ----------------

    fun showUnsaved(
        activity: Activity,
        fileName: String?,
        onSave: () -> Unit,
        onDiscard: () -> Unit
    ) {
        val message = if (fileName.isNullOrBlank()) {
            activity.getString(R.string.unsaved_message)
        } else {
            activity.getString(R.string.unsaved_message_file, fileName)
        }

        // 文件名加粗，明确是在说哪个文件
        val styled = android.text.SpannableString(message)
        if (!fileName.isNullOrBlank()) {
            val start = message.indexOf(fileName)
            if (start >= 0) {
                styled.setSpan(
                    android.text.style.StyleSpan(android.graphics.Typeface.BOLD),
                    start,
                    start + fileName.length,
                    android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }

        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.unsaved_title)
            .setMessage(styled)
            .setPositiveButton(R.string.save) { _, _ -> onSave() }
            .setNegativeButton(R.string.discard) { _, _ -> onDiscard() }
            .setNeutralButton(R.string.cancel, null)
            .show()
    }

    // ---------------- 字体大小 ----------------

    fun showFontSize(
        activity: Activity,
        initial: Int,
        onPreview: (Int) -> Unit,
        onCommit: (Int) -> Unit,
        onCancel: () -> Unit
    ) {
        val density = activity.resources.displayMetrics.density
        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                (24 * density).toInt(), (8 * density).toInt(),
                (24 * density).toInt(), (8 * density).toInt()
            )
        }
        val tvValue = TextView(activity).apply {
            text = activity.getString(R.string.font_size_label, initial)
            textSize = 18f
            setPadding(0, 0, 0, (16 * density).toInt())
        }
        val seekBar = SeekBar(activity).apply {
            max = 30
            progress = initial - 10
        }
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                val size = progress + 10
                tvValue.text = activity.getString(R.string.font_size_label, size)
                onPreview(size)
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
        container.addView(tvValue)
        container.addView(seekBar)

        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.font_size_dialog_title)
            .setView(container)
            .setPositiveButton(R.string.ok) { _, _ -> onCommit(seekBar.progress + 10) }
            .setNegativeButton(R.string.cancel) { _, _ -> onCancel() }
            .setOnCancelListener { onCancel() }
            .show()
    }

    // ---------------- 主题 ----------------

    fun showTheme(activity: Activity, current: Int, onPick: (Int) -> Unit) {
        val labels = arrayOf(
            activity.getString(R.string.theme_system),
            activity.getString(R.string.theme_light),
            activity.getString(R.string.theme_dark)
        )
        val values = intArrayOf(
            AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
            AppCompatDelegate.MODE_NIGHT_NO,
            AppCompatDelegate.MODE_NIGHT_YES
        )
        val checked = values.indexOf(current).coerceAtLeast(0)
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.theme_title)
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                onPick(values[which])
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ---------------- 保存编码 ----------------

    fun showEncoding(activity: Activity, current: String, onPick: (String) -> Unit) {
        val charsets = TextCodec.SUPPORTED.toTypedArray()
        var selectedIndex = charsets.indexOf(current).coerceAtLeast(0)

        val b = DialogEncodingBinding.inflate(activity.layoutInflater)
        b.tvDialogTitle.setText(R.string.choose_encoding)

        val itemBindings = mutableListOf<DialogChoiceItemBinding>()

        fun refreshCheck() {
            itemBindings.forEachIndexed { idx, item ->
                item.ivCheck.visibility =
                    if (idx == selectedIndex) View.VISIBLE else View.INVISIBLE
            }
        }

        charsets.forEachIndexed { i, charset ->
            val item = DialogChoiceItemBinding.inflate(
                activity.layoutInflater, b.choiceContainer, false
            )
            item.tvOptionText.text = charset
            item.root.setOnClickListener {
                selectedIndex = i
                refreshCheck()
            }
            b.choiceContainer.addView(item.root)
            itemBindings.add(item)
        }
        refreshCheck()

        val dialog = MaterialAlertDialogBuilder(activity)
            .setView(b.root)
            .create()

        b.btnCancel.setOnClickListener { dialog.dismiss() }
        b.btnConfirm.setOnClickListener {
            onPick(charsets[selectedIndex])
            dialog.dismiss()
        }

        dialog.show()

        sizeDialog(activity, dialog)
    }

    // ---------------- 跳转到行 ----------------

    fun showGotoLine(
        activity: Activity,
        totalLines: Int,
        onConfirm: (Int) -> Unit
    ) {
        val b = DialogInputBinding.inflate(activity.layoutInflater)
        b.tvDialogTitle.setText(R.string.goto_line_title)
        b.tvDialogSubtitle.text = activity.getString(R.string.goto_line_range, totalLines)
        b.etInput.hint = activity.getString(R.string.goto_line_hint)
        b.etInput.inputType = InputType.TYPE_CLASS_NUMBER

        val dialog = MaterialAlertDialogBuilder(activity)
            .setView(b.root)
            .create()

        b.etInput.doAfterTextChanged { b.tvError.visibility = View.GONE }

        b.btnCancel.setOnClickListener { dialog.dismiss() }
        b.btnConfirm.setOnClickListener {
            val line = b.etInput.text?.toString()?.trim()?.toIntOrNull()
            if (line == null || line < 1 || line > totalLines) {
                b.tvError.text = activity.getString(R.string.goto_line_invalid)
                b.tvError.visibility = View.VISIBLE
                return@setOnClickListener
            }
            dialog.dismiss()
            onConfirm(line)
        }

        dialog.setOnShowListener {
            b.etInput.requestFocus()
            dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        }

        dialog.show()
        sizeDialog(activity, dialog)
    }

    // ---------------- 通用列表弹窗 ----------------

    fun showList(
        activity: Activity,
        titleRes: Int,
        subtitle: String? = null,
        items: List<String>,
        actionTextRes: Int? = null,
        destructiveAction: Boolean = false,
        onPick: (Int) -> Unit,
        onAction: (() -> Unit)? = null
    ) {
        val b = DialogListBinding.inflate(activity.layoutInflater)
        b.tvDialogTitle.setText(titleRes)

        if (subtitle.isNullOrBlank()) {
            b.tvDialogSubtitle.visibility = View.GONE
        } else {
            b.tvDialogSubtitle.visibility = View.VISIBLE
            b.tvDialogSubtitle.text = subtitle
        }

        val dialog = MaterialAlertDialogBuilder(activity)
            .setView(b.root)
            .create()

        items.forEachIndexed { index, label ->
            val item = DialogChoiceItemBinding.inflate(
                activity.layoutInflater, b.listContainer, false
            )
            item.tvOptionText.text = label
            item.ivCheck.visibility = View.GONE
            item.root.setOnClickListener {
                dialog.dismiss()
                onPick(index)
            }
            b.listContainer.addView(item.root)
        }

        if (items.isEmpty()) {
            b.tvEmpty.visibility = View.VISIBLE
            b.listContainer.visibility = View.GONE
        }

        if (actionTextRes != null && onAction != null) {
            b.btnAction.visibility = View.VISIBLE
            b.btnAction.setText(actionTextRes)
            if (destructiveAction) {
                b.btnAction.setTextColor(
                    MaterialColors.getColor(b.root, com.google.android.material.R.attr.colorError)
                )
            }
            b.btnAction.setOnClickListener {
                dialog.dismiss()
                onAction()
            }
        }
        b.btnClose.setOnClickListener { dialog.dismiss() }

        dialog.show()
        sizeDialog(activity, dialog)
    }

    // ---------------- 统计信息 ----------------

    fun showStats(
        activity: Activity,
        fileName: String,
        stats: EditorCommands.EditorStats,
        computeSha256: (suspend () -> Sha256Result)? = null
    ): AlertDialog {
        val b = DialogStatsBinding.inflate(activity.layoutInflater)

        b.tvStatsFileName.text = activity.getString(R.string.stats_file_name, fileName)
        b.tvSummaryChars.text = formatCount(stats.charsNoSpace)
        b.tvSummaryLines.text = formatCount(stats.totalLines)
        b.tvSummaryWords.text = formatCount(stats.words)

        // 两组常用数据直接展示
        addStatsSection(
            activity, b.statsSections, R.string.stats_group_chars,
            listOf(
                R.string.stat_chars_with_space to stats.charsWithSpace,
                R.string.stat_chars_no_space to stats.charsNoSpace,
                R.string.stat_invisible_chars to stats.invisibleChars
            )
        )
        addStatsSection(
            activity, b.statsSections, R.string.stats_group_paragraphs,
            listOf(
                R.string.stat_total_lines to stats.totalLines,
                R.string.stat_nonempty_lines to stats.nonEmptyLines,
                R.string.stat_paragraphs to stats.paragraphs,
                R.string.stat_words to stats.words
            )
        )

        // 低频项默认折叠
        val moreSection = addStatsSection(
            activity, b.statsSections, R.string.stats_group_more,
            listOf(
                R.string.stat_letters to stats.letters,
                R.string.stat_digits to stats.digits,
                R.string.stat_chinese_chars to stats.chineseChars,
                R.string.stat_max_line_width to stats.maxLineWidth
            )
        )
        moreSection.visibility = View.GONE

        b.rowMoreStats.setOnClickListener {
            val expand = moreSection.visibility != View.VISIBLE
            moreSection.visibility = if (expand) View.VISIBLE else View.GONE
            b.rowMoreStats.setText(if (expand) R.string.stats_less else R.string.stats_more)
        }

        var currentHash: String? = null
        b.tvSha256Value.text = if (computeSha256 == null) {
            activity.getString(R.string.stats_sha256_unavailable)
        } else {
            activity.getString(R.string.stats_sha256_computing)
        }

        val shell = DialogShell.build(activity, R.string.stats_title)
        shell.shellContent.addView(b.root)
        val dialog = DialogShell.show(activity, shell)

        DialogShell.addFooterButton(activity, shell, R.string.ok) { dialog.dismiss() }

        b.btnCopySha256.setOnClickListener {
            val hash = currentHash
            if (hash == null) {
                AppTips.showShort(b.root, R.string.stats_sha256_unavailable)
            } else {
                val cm = activity.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                        as android.content.ClipboardManager
                cm.setPrimaryClip(android.content.ClipData.newPlainText("SHA-256", hash))
                AppTips.showShort(b.root, R.string.stats_sha256_copied)
            }
        }

        if (computeSha256 != null) {
            val owner = activity as? androidx.lifecycle.LifecycleOwner
            if (owner == null) {
                b.tvSha256Value.text = activity.getString(R.string.stats_sha256_unavailable)
            } else {
                owner.lifecycleScope.launch {
                    val result = try {
                        computeSha256()
                    } catch (_: Exception) {
                        Sha256Result.Unavailable
                    }
                    when (result) {
                        is Sha256Result.DiskFile -> {
                            currentHash = result.hash
                            b.tvSha256Value.text = result.hash
                        }
                        is Sha256Result.CurrentContent -> {
                            currentHash = result.hash
                            b.tvSha256Value.text = activity.getString(
                                R.string.stats_sha256_current_content, result.hash
                            )
                        }
                        Sha256Result.Unavailable -> {
                            currentHash = null
                            b.tvSha256Value.text =
                                activity.getString(R.string.stats_sha256_unavailable)
                        }
                    }
                }
            }
        }

        return dialog
    }

    /** 一组统计：小标题 + 若干「标签 … 等宽数值」行 */
    private fun addStatsSection(
        activity: Activity,
        parent: android.widget.LinearLayout,
        @StringRes titleRes: Int,
        rows: List<Pair<Int, Int>>
    ): android.view.View {
        val density = activity.resources.displayMetrics.density
        val section = android.widget.LinearLayout(activity).apply {
            orientation = android.widget.LinearLayout.VERTICAL
        }

        val title = android.widget.TextView(activity).apply {
            setText(titleRes)
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(
                MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimary)
            )
            setPadding(0, (12 * density).toInt(), 0, (2 * density).toInt())
        }
        section.addView(title)

        rows.forEach { (labelRes, value) ->
            val row = android.widget.LinearLayout(activity).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL
                setPadding(0, (6 * density).toInt(), 0, (6 * density).toInt())
            }

            val label = android.widget.TextView(activity).apply {
                setText(labelRes)
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14f)
                setTextColor(
                    MaterialColors.getColor(
                        this, com.google.android.material.R.attr.colorOnSurfaceVariant
                    )
                )
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f
                )
            }

            val valueView = android.widget.TextView(activity).apply {
                text = formatCount(value)
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14f)
                typeface = android.graphics.Typeface.MONOSPACE
                setTextColor(
                    MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface)
                )
            }

            row.addView(label)
            row.addView(valueView)
            section.addView(row)
        }

        parent.addView(section)
        return section
    }

    /** 带千分位，按系统语言格式化 */
    private fun formatCount(value: Int): String =
        java.text.NumberFormat.getInstance().format(value)

    // ---------------- 特殊字符面板 ----------------

    /**
     * 特殊字符面板：按语义分组，用 Chip 呈现，点一下直接插入并关闭。
     *
     * @param groups 每组是 (分组标题, 若干 (标签, 实际插入的字符))
     */
    fun showSpecialCharPanel(
        activity: Activity,
        groups: List<Pair<Int, List<Pair<String, String>>>>,
        showSpecialInitially: Boolean,
        onToggleShow: (Boolean) -> Unit,
        onPick: (String) -> Unit
    ) {
        val b = DialogSpecialCharBinding.inflate(activity.layoutInflater)
        b.switchShowSpecial.isChecked = showSpecialInitially

        b.switchShowSpecial.setOnCheckedChangeListener { _, checked -> onToggleShow(checked) }
        b.rowShowSpecial.setOnClickListener {
            b.switchShowSpecial.isChecked = !b.switchShowSpecial.isChecked
        }

        val density = activity.resources.displayMetrics.density

        // 先把壳建出来，Chip 点击回调里要用到 dialog
        val shell = DialogShell.build(activity, R.string.special_chars_title)
        shell.shellContent.addView(b.root)
        val dialog = DialogShell.show(activity, shell)

        groups.forEach { (titleRes, items) ->
            val title = TextView(activity).apply {
                setText(titleRes)
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12f)
                setTextColor(
                    MaterialColors.getColor(
                        this, com.google.android.material.R.attr.colorPrimary
                    )
                )
                setPadding(0, (10 * density).toInt(), 0, 0)
            }
            b.charGroups.addView(title)

            val chipGroup = com.google.android.material.chip.ChipGroup(activity).apply {
                isSingleLine = false
                setChipSpacingHorizontal((6 * density).toInt())
            }

            items.forEach { (label, value) ->
                val chip = com.google.android.material.chip.Chip(
                    activity, null, com.google.android.material.R.attr.chipStyle
                ).apply {
                    text = label
                    setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12f)
                    isCheckable = false
                    setEnsureMinTouchTargetSize(false)
                }
                chip.setOnClickListener {
                    onPick(value)
                    dialog.dismiss()
                }
                chipGroup.addView(chip)
            }

            b.charGroups.addView(chipGroup)
        }

        DialogShell.addFooterButton(activity, shell, R.string.special_rules_entry) {
            showSpecialCharRules(activity)
        }
        DialogShell.addFooterButton(activity, shell, R.string.close) { dialog.dismiss() }
    }

    /** 特殊字符的显示规则：字符叫什么、在编辑器里显示成什么符号 */
    fun showSpecialCharRules(activity: Activity) {
        val shell = DialogShell.build(activity, R.string.special_rules_title)
        val density = activity.resources.displayMetrics.density

        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
        }

        SpecialCharRenderer.displayRules().forEach { (_, symbol, labelRes) ->
            val row = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(0, (7 * density).toInt(), 0, (7 * density).toInt())
            }

            val name = TextView(activity).apply {
                setText(labelRes)
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14f)
                setTextColor(
                    MaterialColors.getColor(
                        this, com.google.android.material.R.attr.colorOnSurfaceVariant
                    )
                )
                layoutParams = LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
                )
            }

            val symbolView = TextView(activity).apply {
                text = symbol
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 16f)
                typeface = android.graphics.Typeface.MONOSPACE
                setTextColor(
                    MaterialColors.getColor(
                        this, com.google.android.material.R.attr.colorOnSurface
                    )
                )
            }

            row.addView(name)
            row.addView(symbolView)
            container.addView(row)
        }

        shell.shellContent.addView(container)
        val dialog = DialogShell.show(activity, shell)
        DialogShell.addFooterButton(activity, shell, R.string.close) { dialog.dismiss() }
    }

    // ---------------- 快速片段 ----------------

    fun showSnippets(
        activity: Activity,
        snippets: List<Snippet>,
        onPick: (Snippet) -> Unit,
        onAdd: () -> Unit,
        onDelete: (Snippet) -> Unit
    ) {
        val shell = DialogShell.build(activity, R.string.menu_snippets)
        val density = activity.resources.displayMetrics.density

        val container = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        shell.shellContent.addView(container)

        val current = snippets.toMutableList()
        var dialogRef: AlertDialog? = null

        fun rebuild() {
            container.removeAllViews()

            if (current.isEmpty()) {
                container.addView(TextView(activity).apply {
                    setText(R.string.snippets_empty)
                    setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14f)
                    setTextColor(
                        MaterialColors.getColor(
                            this, com.google.android.material.R.attr.colorOnSurfaceVariant
                        )
                    )
                    setPadding(0, (16 * density).toInt(), 0, (16 * density).toInt())
                })
                return
            }

            current.forEach { snippet ->
                val row = LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    setPadding(0, (6 * density).toInt(), 0, (6 * density).toInt())
                }

                val name = TextView(activity).apply {
                    text = snippet.name
                    setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 15f)
                    setTextColor(
                        MaterialColors.getColor(
                            this, com.google.android.material.R.attr.colorOnSurface
                        )
                    )
                    layoutParams = LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
                    )
                }

                val delete = TextView(activity).apply {
                    setText(R.string.snippets_delete)
                    setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12f)
                    setTextColor(
                        MaterialColors.getColor(
                            this, com.google.android.material.R.attr.colorError
                        )
                    )
                    setPadding(
                        (10 * density).toInt(), (6 * density).toInt(),
                        (4 * density).toInt(), (6 * density).toInt()
                    )
                }

                row.setOnClickListener {
                    dialogRef?.dismiss()
                    onPick(snippet)
                }
                delete.setOnClickListener {
                    current.remove(snippet)
                    onDelete(snippet)
                    rebuild()
                }

                row.addView(name)
                row.addView(delete)
                container.addView(row)
            }
        }

        rebuild()

        val dialog = DialogShell.show(activity, shell)
        dialogRef = dialog

        DialogShell.addFooterButton(activity, shell, R.string.snippets_add) {
            dialog.dismiss()
            onAdd()
        }
        DialogShell.addFooterButton(activity, shell, R.string.close) { dialog.dismiss() }
    }

    fun showSnippetEditor(
        activity: Activity,
        initial: Snippet?,
        onSave: (Snippet) -> Unit
    ) {
        val b = DialogSnippetEditBinding.inflate(activity.layoutInflater)
        b.etSnippetName.setText(initial?.name.orEmpty())
        b.etSnippetContent.setText(initial?.content.orEmpty())

        val shell = DialogShell.build(
            activity,
            if (initial == null) R.string.snippets_add else R.string.snippets_edit
        )
        shell.shellContent.addView(b.root)

        val dialog = DialogShell.show(activity, shell)

        DialogShell.addFooterButton(activity, shell, R.string.save) {
            val name = b.etSnippetName.text?.toString()?.trim().orEmpty()
            if (name.isEmpty()) {
                AppTips.showShort(b.root, R.string.snippet_name_required)
                return@addFooterButton
            }
            onSave(Snippet(name, b.etSnippetContent.text?.toString().orEmpty()))
            dialog.dismiss()
        }
        DialogShell.addFooterButton(activity, shell, R.string.cancel) { dialog.dismiss() }
    }

    // ---------------- 统一尺寸 ----------------

    /** 自定义内容弹窗统一按屏幕宽度 92% 显示，避免有的宽有的窄 */
    private fun sizeDialog(activity: Activity, dialog: Dialog) {
        dialog.window?.setLayout(
            (activity.resources.displayMetrics.widthPixels * 0.92f).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    // ---------------- 状态文字颜色辅助 ----------------

    private fun showStatusError(tv: TextView, text: String) {
        tv.text = text
        tv.setTextColor(
            MaterialColors.getColor(tv, com.google.android.material.R.attr.colorError)
        )
    }

    private fun showStatusPrimary(tv: TextView, text: String) {
        tv.text = text
        tv.setTextColor(
            MaterialColors.getColor(tv, com.google.android.material.R.attr.colorPrimary)
        )
    }

    private fun showStatusNeutral(tv: TextView, text: String) {
        tv.text = text
        tv.setTextColor(
            MaterialColors.getColor(tv, com.google.android.material.R.attr.colorOnSurfaceVariant)
        )
    }
}
