package com.sunwenqing.plainpad

import android.app.Activity
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sunwenqing.plainpad.databinding.DialogChoiceBinding
import com.sunwenqing.plainpad.databinding.DialogChoiceItemBinding
import com.sunwenqing.plainpad.databinding.DialogEncodingBinding
import com.sunwenqing.plainpad.databinding.DialogFindBinding
import com.sunwenqing.plainpad.databinding.DialogReplaceBinding
import com.sunwenqing.plainpad.databinding.DialogSpecialCharBinding
import com.sunwenqing.plainpad.databinding.DialogStatsBinding

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
        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 30, 50, 30)
        }
        val tvValue = TextView(activity).apply {
            text = activity.getString(R.string.font_size_label, initial)
            textSize = 18f
            setPadding(0, 0, 0, 20)
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

    fun showFind(activity: Activity, onFind: (String, Boolean) -> Unit) {
        val binding = DialogFindBinding.inflate(activity.layoutInflater)
        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.menu_find)
            .setView(binding.root)
            .setNegativeButton(R.string.close, null)
            .create()
        binding.btnFindNext.setOnClickListener { onFind(binding.etFind.text.toString(), true) }
        binding.btnFindPrev.setOnClickListener { onFind(binding.etFind.text.toString(), false) }
        dialog.show()
    }

    // ---------------- 替换 ----------------

    fun showReplace(
        activity: Activity,
        onReplace: (String, String) -> Unit,
        onReplaceAll: (String, String) -> Unit
    ) {
        val binding = DialogReplaceBinding.inflate(activity.layoutInflater)
        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.menu_replace)
            .setView(binding.root)
            .setNegativeButton(R.string.close, null)
            .create()
        binding.btnReplace.setOnClickListener {
            onReplace(binding.etFind.text.toString(), binding.etReplace.text.toString())
        }
        binding.btnReplaceAll.setOnClickListener {
            onReplaceAll(binding.etFind.text.toString(), binding.etReplace.text.toString())
        }
        dialog.show()
    }

    // ---------------- 跳转到行 ----------------

    fun showGotoLine(
        activity: Activity,
        totalLines: Int,
        onConfirm: (Int) -> Unit
    ) {
        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 30, 50, 20)
        }

        val tip = TextView(activity).apply {
            text = activity.getString(R.string.goto_line_range, totalLines)
            textSize = 13f
            setPadding(0, 0, 0, 20)
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
                Toast.makeText(
                    activity,
                    activity.getString(R.string.goto_line_invalid),
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }
            onConfirm(line)
            dialog.dismiss()
        }
    }

    // ---------------- 统计信息 ----------------

    fun showStats(activity: Activity, fileName: String, s: EditorCommands.EditorStats) {
        val binding = DialogStatsBinding.inflate(activity.layoutInflater)

        binding.tvStatsFileName.text = activity.getString(R.string.stats_file_name, fileName)

        binding.tvCharsWithSpace.text = s.charsWithSpace.toString()
        binding.tvCharsNoSpace.text = s.charsNoSpace.toString()
        binding.tvInvisibleChars.text = s.invisibleChars.toString()
        binding.tvLetters.text = s.letters.toString()
        binding.tvDigits.text = s.digits.toString()
        binding.tvChineseChars.text = s.chineseChars.toString()
        binding.tvTotalLines.text = s.totalLines.toString()
        binding.tvNonEmptyLines.text = s.nonEmptyLines.toString()
        binding.tvParagraphs.text = s.paragraphs.toString()
        binding.tvWords.text = s.words.toString()
        binding.tvMaxLineWidth.text = s.maxLineWidth.toString()

        MaterialAlertDialogBuilder(activity)
            .setView(binding.root)
            .setPositiveButton(R.string.ok, null)
            .show()
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
}