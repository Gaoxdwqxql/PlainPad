package com.sunwenqing.plainpad

import android.app.Activity
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sunwenqing.plainpad.databinding.DialogChoiceBinding
import com.sunwenqing.plainpad.databinding.DialogChoiceItemBinding
import com.sunwenqing.plainpad.databinding.DialogEncodingBinding
import com.sunwenqing.plainpad.databinding.DialogFindBinding
import com.sunwenqing.plainpad.databinding.DialogReplaceBinding
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
        onSave: () -> Unit,
        onDiscard: () -> Unit
    ) {
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.unsaved_title)
            .setMessage(R.string.unsaved_message)
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

        dialog.window?.setLayout(
            (activity.resources.displayMetrics.widthPixels * 0.9).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    // ---------------- 最近文件 ----------------

    fun showRecent(
        activity: Activity,
        files: List<RecentFile>,
        onOpen: (RecentFile) -> Unit,
        onClear: () -> Unit
    ) {
        if (files.isEmpty()) return
        val names = files.map { it.name }.toTypedArray()
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.recent_title)
            .setItems(names) { _, which -> onOpen(files[which]) }
            .setNeutralButton(R.string.clear) { _, _ -> onClear() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ---------------- 查找 ----------------

    fun showFind(
        activity: Activity,
        onFind: (query: String, forward: Boolean, options: SearchOptions, isFirstSearch: Boolean) -> EditorSearchHelper.FindFeedback,
        onQueryChanged: (String, SearchOptions) -> Unit = { _, _ -> },
        onClosed: () -> Unit = {}
    ) {
        val binding = DialogFindBinding.inflate(activity.layoutInflater)
        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.menu_find)
            .setView(binding.root)
            .setNegativeButton(R.string.close, null)
            .create()

        var hasSearched = false

        fun options() = SearchOptions(
            regex = binding.cbRegex.isChecked,
            caseSensitive = binding.cbCaseSensitive.isChecked,
            fromStart = binding.cbFromStart.isChecked
        )

        fun updateHint() {
            val o = options()
            binding.tvSearchHint.text = activity.getString(
                when {
                    o.regex && o.caseSensitive -> R.string.search_hint_regex_case
                    o.regex -> R.string.search_hint_regex
                    o.caseSensitive -> R.string.search_hint_case
                    else -> R.string.search_hint_plain
                }
            )
        }

        fun updateInitialStatus() {
            val fromStart = binding.cbFromStart.isChecked
            showStatusNeutral(
                binding.tvSearchStatus,
                activity.getString(
                    if (fromStart) R.string.search_status_initial_from_start
                    else R.string.search_status_initial_from_cursor
                )
            )
        }

        fun updatePrevButtonState() {
            // 首次且勾选"从头开始"时，"查找上一个"禁用
            val disablePrev = !hasSearched && binding.cbFromStart.isChecked
            binding.btnFindPrev.isEnabled = !disablePrev
            binding.btnFindPrev.alpha = if (disablePrev) 0.4f else 1f
        }

        fun resetToInitialState() {
            hasSearched = false
            updateInitialStatus()
            updatePrevButtonState()
            // 条件一变就重画全部匹配高亮
            onQueryChanged(binding.etFind.text?.toString().orEmpty(), options())
        }

        binding.cbRegex.setOnCheckedChangeListener { _, _ -> updateHint(); resetToInitialState() }
        binding.cbCaseSensitive.setOnCheckedChangeListener { _, _ -> updateHint(); resetToInitialState() }
        binding.cbFromStart.setOnCheckedChangeListener { _, _ -> resetToInitialState() }
        binding.etFind.doAfterTextChanged { resetToInitialState() }

        fun runFind(forward: Boolean) {
            val query = binding.etFind.text?.toString().orEmpty()
            if (query.isEmpty()) {
                showStatusError(
                    binding.tvSearchStatus,
                    activity.getString(R.string.search_empty_query)
                )
                return
            }

            val isFirst = !hasSearched
            hasSearched = true
            updatePrevButtonState()

            when (val fb = onFind(query, forward, options(), isFirst)) {
                is EditorSearchHelper.FindFeedback.Found ->
                    showStatusPrimary(
                        binding.tvSearchStatus,
                        activity.getString(R.string.search_status_found)
                    )
                is EditorSearchHelper.FindFeedback.NotFound ->
                    showStatusError(
                        binding.tvSearchStatus,
                        activity.getString(R.string.search_status_not_found)
                    )
                is EditorSearchHelper.FindFeedback.Invalid ->
                    showStatusError(
                        binding.tvSearchStatus,
                        activity.getString(R.string.search_status_invalid_regex, fb.message)
                    )
            }
        }

        binding.btnFindNext.setOnClickListener { runFind(true) }
        binding.btnFindPrev.setOnClickListener { runFind(false) }

        updateHint()
        resetToInitialState()

        dialog.setOnShowListener {
            binding.etFind.requestFocus()
            dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        }
        dialog.setOnDismissListener { onClosed() }
        dialog.show()
    }

    // ---------------- 替换 ----------------

    fun showReplace(
        activity: Activity,
        onReplace: (query: String, replacement: String, options: SearchOptions, isFirstSearch: Boolean) -> EditorSearchHelper.ReplaceFeedback,
        onReplaceAll: (query: String, replacement: String, options: SearchOptions) -> EditorSearchHelper.ReplaceFeedback,
        onQueryChanged: (String, SearchOptions) -> Unit = { _, _ -> },
        onClosed: () -> Unit = {}
    ) {
        val binding = DialogReplaceBinding.inflate(activity.layoutInflater)
        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.menu_replace)
            .setView(binding.root)
            .setNegativeButton(R.string.close, null)
            .create()

        var hasSearched = false

        fun options() = SearchOptions(
            regex = binding.cbRegex.isChecked,
            caseSensitive = binding.cbCaseSensitive.isChecked,
            fromStart = binding.cbFromStart.isChecked
        )

        fun updateHint() {
            val o = options()
            binding.tvSearchHint.text = activity.getString(
                when {
                    o.regex && o.caseSensitive -> R.string.search_hint_regex_case
                    o.regex -> R.string.search_hint_regex
                    o.caseSensitive -> R.string.search_hint_case
                    else -> R.string.search_hint_plain
                }
            )
        }

        fun resetToInitialState() {
            hasSearched = false
            val fromStart = binding.cbFromStart.isChecked
            showStatusNeutral(
                binding.tvSearchStatus,
                activity.getString(
                    if (fromStart) R.string.search_status_initial_from_start
                    else R.string.search_status_initial_from_cursor
                )
            )
            onQueryChanged(binding.etFind.text?.toString().orEmpty(), options())
        }

        binding.cbRegex.setOnCheckedChangeListener { _, _ -> updateHint(); resetToInitialState() }
        binding.cbCaseSensitive.setOnCheckedChangeListener { _, _ -> updateHint(); resetToInitialState() }
        binding.cbFromStart.setOnCheckedChangeListener { _, _ -> resetToInitialState() }
        binding.etFind.doAfterTextChanged { resetToInitialState() }
        binding.etReplace.doAfterTextChanged { resetToInitialState() }

        fun render(fb: EditorSearchHelper.ReplaceFeedback) {
            when (fb) {
                is EditorSearchHelper.ReplaceFeedback.Replaced -> {
                    hasSearched = true
                    if (fb.count == 1) {
                        showStatusPrimary(
                            binding.tvSearchStatus,
                            activity.getString(R.string.replace_status_replaced_current)
                        )
                    } else {
                        showStatusPrimary(
                            binding.tvSearchStatus,
                            activity.getString(R.string.replace_status_replaced, fb.count)
                        )
                    }
                }
                is EditorSearchHelper.ReplaceFeedback.Located -> {
                    hasSearched = true
                    showStatusPrimary(
                        binding.tvSearchStatus,
                        activity.getString(R.string.replace_status_located)
                    )
                }
                is EditorSearchHelper.ReplaceFeedback.NotFound ->
                    showStatusError(
                        binding.tvSearchStatus,
                        activity.getString(R.string.search_status_not_found)
                    )
                is EditorSearchHelper.ReplaceFeedback.Invalid ->
                    showStatusError(
                        binding.tvSearchStatus,
                        activity.getString(R.string.search_status_invalid_regex, fb.message)
                    )
            }
        }

        binding.btnReplace.setOnClickListener {
            val q = binding.etFind.text?.toString().orEmpty()
            if (q.isEmpty()) {
                showStatusError(
                    binding.tvSearchStatus,
                    activity.getString(R.string.search_empty_query)
                )
                return@setOnClickListener
            }
            val r = binding.etReplace.text?.toString().orEmpty()
            val isFirst = !hasSearched
            render(onReplace(q, r, options(), isFirst))
        }

        binding.btnReplaceAll.setOnClickListener {
            val q = binding.etFind.text?.toString().orEmpty()
            if (q.isEmpty()) {
                showStatusError(
                    binding.tvSearchStatus,
                    activity.getString(R.string.search_empty_query)
                )
                return@setOnClickListener
            }
            val r = binding.etReplace.text?.toString().orEmpty()
            render(onReplaceAll(q, r, options()))
        }

        updateHint()
        resetToInitialState()

        dialog.setOnShowListener {
            binding.etFind.requestFocus()
            dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        }
        dialog.setOnDismissListener { onClosed() }
        dialog.show()
    }

    // ---------------- 跳转到行 ----------------

    fun showGotoLine(
        activity: Activity,
        totalLines: Int,
        onConfirm: (Int) -> Unit
    ) {
        val density = activity.resources.displayMetrics.density
        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                (24 * density).toInt(), (8 * density).toInt(),
                (24 * density).toInt(), (8 * density).toInt()
            )
        }

        val tip = TextView(activity).apply {
            text = activity.getString(R.string.goto_line_range, totalLines)
            textSize = 13f
            setPadding(0, 0, 0, (16 * density).toInt())
        }

        val input = EditText(activity).apply {
            hint = activity.getString(R.string.goto_line_hint)
            inputType = InputType.TYPE_CLASS_NUMBER
            isSingleLine = true
        }

        container.addView(tip)
        container.addView(input)

        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.goto_line_title)
            .setView(container)
            .setPositiveButton(R.string.ok, null)
            .setNegativeButton(R.string.cancel, null)
            .create()

        dialog.show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
            val text = input.text?.toString()?.trim().orEmpty()
            val line = text.toIntOrNull()
            if (line == null || line < 1 || line > totalLines) {
                AppTips.showShort(container, activity.getString(R.string.goto_line_invalid))
                return@setOnClickListener
            }
            onConfirm(line)
            dialog.dismiss()
        }
    }

    // ---------------- 统计信息 ----------------

    fun showStats(
        activity: Activity,
        fileName: String,
        stats: EditorCommands.EditorStats,
        computeSha256: (suspend () -> Sha256Result)? = null
    ): AlertDialog {
        val binding = DialogStatsBinding.inflate(activity.layoutInflater)

        binding.tvStatsFileName.text = activity.getString(R.string.stats_file_name, fileName)

        binding.tvCharsWithSpace.text = stats.charsWithSpace.toString()
        binding.tvCharsNoSpace.text = stats.charsNoSpace.toString()
        binding.tvInvisibleChars.text = stats.invisibleChars.toString()
        binding.tvLetters.text = stats.letters.toString()
        binding.tvDigits.text = stats.digits.toString()
        binding.tvChineseChars.text = stats.chineseChars.toString()
        binding.tvTotalLines.text = stats.totalLines.toString()
        binding.tvNonEmptyLines.text = stats.nonEmptyLines.toString()
        binding.tvParagraphs.text = stats.paragraphs.toString()
        binding.tvWords.text = stats.words.toString()
        binding.tvMaxLineWidth.text = stats.maxLineWidth.toString()

        var currentHash: String? = null

        binding.tvSha256Value.text = if (computeSha256 == null) {
            activity.getString(R.string.stats_sha256_unavailable)
        } else {
            activity.getString(R.string.stats_sha256_computing)
        }

        binding.btnCopySha256.setOnClickListener {
            val hash = currentHash
            if (hash == null) {
                AppTips.showShort(binding.root, R.string.stats_sha256_unavailable)
            } else {
                val cm = activity.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                        as android.content.ClipboardManager
                cm.setPrimaryClip(android.content.ClipData.newPlainText("SHA-256", hash))
                AppTips.showShort(binding.root, R.string.stats_sha256_copied)
            }
        }

        val dialog = MaterialAlertDialogBuilder(activity)
            .setView(binding.root)
            .setPositiveButton(R.string.ok, null)
            .show()

        if (computeSha256 != null) {
            val owner = activity as? androidx.lifecycle.LifecycleOwner
            if (owner == null) {
                binding.tvSha256Value.text = activity.getString(R.string.stats_sha256_unavailable)
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
                            binding.tvSha256Value.text = result.hash
                        }
                        is Sha256Result.CurrentContent -> {
                            currentHash = result.hash
                            binding.tvSha256Value.text = activity.getString(
                                R.string.stats_sha256_current_content, result.hash
                            )
                        }
                        Sha256Result.Unavailable -> {
                            currentHash = null
                            binding.tvSha256Value.text =
                                activity.getString(R.string.stats_sha256_unavailable)
                        }
                    }
                }
            }
        }

        return dialog
    }

    // ---------------- 特殊字符面板 ----------------

    fun showSpecialCharPanel(
        activity: Activity,
        items: List<Pair<String, String>>,
        showSpecialInitially: Boolean,
        onToggleShow: (Boolean) -> Unit,
        onPick: (String) -> Unit
    ) {
        val b = DialogSpecialCharBinding.inflate(activity.layoutInflater)
        b.tvDialogTitle.setText(R.string.special_chars_title)
        b.switchShowSpecial.isChecked = showSpecialInitially

        val dialog = MaterialAlertDialogBuilder(activity)
            .setView(b.root)
            .create()

        b.switchShowSpecial.setOnCheckedChangeListener { _, checked ->
            onToggleShow(checked)
        }
        b.rowShowSpecial.setOnClickListener {
            b.switchShowSpecial.isChecked = !b.switchShowSpecial.isChecked
        }

        var selectedIndex = -1
        val itemBindings = mutableListOf<DialogChoiceItemBinding>()

        fun refreshCheck() {
            itemBindings.forEachIndexed { idx, item ->
                item.ivCheck.visibility =
                    if (idx == selectedIndex) View.VISIBLE else View.INVISIBLE
            }
        }

        items.forEachIndexed { i, (label, _) ->
            val item = DialogChoiceItemBinding.inflate(
                activity.layoutInflater, b.insertContainer, false
            )
            item.tvOptionText.text = label
            item.ivCheck.visibility = View.INVISIBLE
            item.root.setOnClickListener {
                selectedIndex = i
                refreshCheck()
            }
            b.insertContainer.addView(item.root)
            itemBindings.add(item)
        }
        refreshCheck()

        b.btnCancel.setOnClickListener { dialog.dismiss() }
        b.btnConfirm.setOnClickListener {
            if (selectedIndex >= 0) {
                onPick(items[selectedIndex].second)
            }
            dialog.dismiss()
        }

        dialog.show()

        dialog.window?.setLayout(
            (activity.resources.displayMetrics.widthPixels * 0.9).toInt(),
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