package com.sunwenqing.plainpad

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.color.MaterialColors
import com.sunwenqing.plainpad.databinding.ActivityMainBinding

/**
 * 内嵌查找栏（VS Code / Notepad++ 风格），取代原来的查找/替换弹窗。
 *
 * 交互：
 * - 打开时若编辑器里有单行选区，自动带入查找词
 * - 输入即统计并高亮全部匹配，输入框右侧显示 "3/12"
 * - 上/下按钮按匹配下标前后移动，到末尾自动回绕
 * - 选项 Chip 里的「替换」把面板展开成两行，出现替换输入框与 替换 / 全部替换
 */
class FindBarController(
    private val activity: AppCompatActivity,
    private val binding: ActivityMainBinding,
    private val getEditor: () -> PlainEditText,
    private val isLargeFileMode: () -> Boolean,
    private val isReadOnly: () -> Boolean,
    private val showTip: (String) -> Unit
) {

    private val handler = Handler(Looper.getMainLooper())
    private var refreshRunnable: Runnable? = null

    private var matches: List<IntRange> = emptyList()
    private var replaceMode = false

    /** 用于丢弃过期的收起动画回调（快速开关时避免被误隐藏） */
    private var visibilityToken = 0

    val isOpen: Boolean
        get() = binding.findBar.visibility == View.VISIBLE

    fun setup() {
        binding.etFindInput.doAfterTextChanged { scheduleRefresh() }
        binding.cbFindRegex.setOnCheckedChangeListener { _, _ ->
            syncHint()
            scheduleRefresh()
        }
        binding.cbFindCase.setOnCheckedChangeListener { _, _ -> scheduleRefresh() }
        binding.cbFindFromStart.setOnCheckedChangeListener { _, _ -> scheduleRefresh() }

        binding.btnFindPrev.setOnClickListener { jump(forward = false) }
        binding.btnFindNext.setOnClickListener { jump(forward = true) }
        binding.btnFindClose.setOnClickListener { close() }
        binding.btnToggleReplace.setOnClickListener { setReplaceMode(!replaceMode) }

        binding.btnReplaceCurrent.setOnClickListener { replaceCurrent() }
        binding.btnReplaceAllInBar.setOnClickListener { replaceAll() }
    }

    fun open(replace: Boolean) {
        if (isLargeFileMode()) {
            showTip(activity.getString(R.string.large_file_feature_disabled))
            return
        }

        binding.findBar.visibility = View.VISIBLE
        binding.findBar.alpha = 0f
        binding.findBar.translationY = -6f * activity.resources.displayMetrics.density
        binding.findBar.animate().alpha(1f).translationY(0f).setDuration(160L).start()
        visibilityToken++

        setReplaceMode(replace)
        syncHint()

        // 只读文档允许查找，但替换相关控件直接禁用
        val readOnly = isReadOnly()
        binding.etReplaceInput.isEnabled = !readOnly
        binding.btnReplaceCurrent.isEnabled = !readOnly
        binding.btnReplaceAllInBar.isEnabled = !readOnly

        // 编辑器里有单行选区时直接带进来
        val editor = getEditor()
        val s = editor.selectionStart.coerceAtLeast(0)
        val e = editor.selectionEnd.coerceAtLeast(0)
        if (e > s) {
            val selected = editor.text?.subSequence(s, e)?.toString().orEmpty()
            if (selected.isNotEmpty() && !selected.contains('\n')) {
                binding.etFindInput.setText(selected)
                binding.etFindInput.setSelection(selected.length)
            }
        }

        binding.etFindInput.requestFocus()
        val imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(binding.etFindInput, InputMethodManager.SHOW_IMPLICIT)
        scheduleRefresh(immediate = true)
    }

    fun close() {
        val bar = binding.findBar
        if (bar.visibility != View.VISIBLE) return

        val token = ++visibilityToken
        bar.animate().alpha(0f)
            .translationY(-6f * activity.resources.displayMetrics.density)
            .setDuration(120L)
            .withEndAction {
                if (token == visibilityToken) {
                    bar.visibility = View.GONE
                    bar.alpha = 1f
                    bar.translationY = 0f
                }
            }
            .start()

        refreshRunnable?.let { handler.removeCallbacks(it) }
        refreshRunnable = null
        matches = emptyList()
        SearchHighlighter.clear(getEditor())

        val imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.etFindInput.windowToken, 0)
    }

    private fun setReplaceMode(on: Boolean) {
        replaceMode = on
        binding.findReplaceRow.visibility = if (on) View.VISIBLE else View.GONE
        binding.btnToggleReplace.setText(if (on) R.string.menu_find else R.string.replace)
    }

    /** 只有「正则表达式」需要解释，其余两个选项看标签就懂 */
    private fun syncHint() {
        binding.tvSearchHint.visibility =
            if (binding.cbFindRegex.isChecked) View.VISIBLE else View.GONE
    }

    private fun options() = SearchOptions(
        regex = binding.cbFindRegex.isChecked,
        caseSensitive = binding.cbFindCase.isChecked,
        fromStart = binding.cbFindFromStart.isChecked
    )

    // ---------------- 统计与高亮 ----------------

    private fun scheduleRefresh(immediate: Boolean = false) {
        refreshRunnable?.let { handler.removeCallbacks(it) }
        val r = Runnable { refreshMatches() }
        refreshRunnable = r
        if (immediate) handler.post(r) else handler.postDelayed(r, REFRESH_DELAY_MS)
    }

    private fun refreshMatches() {
        val editor = getEditor()
        val query = binding.etFindInput.text?.toString().orEmpty()

        if (query.isEmpty()) {
            matches = emptyList()
            SearchHighlighter.clear(editor)
            binding.tvFindCount.visibility = View.GONE
            setNavEnabled(false)
            return
        }

        val text = editor.text?.toString().orEmpty()
        matches = EditorSearchHelper.findAll(text, query, options())
        SearchHighlighter.applyRanges(editor, matches, highlightColor())
        setNavEnabled(matches.isNotEmpty())
        updateCount()
    }

    private fun setNavEnabled(enabled: Boolean) {
        binding.btnFindPrev.isEnabled = enabled
        binding.btnFindNext.isEnabled = enabled
        binding.btnFindPrev.alpha = if (enabled) 1f else 0.35f
        binding.btnFindNext.alpha = if (enabled) 1f else 0.35f
    }

    private fun highlightColor(): Int {
        val primary = MaterialColors.getColor(
            binding.root, com.google.android.material.R.attr.colorPrimary
        )
        return (primary and 0x00FFFFFF) or 0x33000000
    }

    /** 光标落在哪个匹配里，没有则 -1 */
    private fun currentIndex(): Int {
        val cursor = getEditor().selectionStart
        return matches.indexOfFirst { cursor in it.first..(it.last + 1) }
    }

    private fun updateCount() {
        val total = matches.size
        if (total == 0) {
            binding.tvFindCount.visibility = View.VISIBLE
            binding.tvFindCount.text = activity.getString(R.string.search_status_not_found)
            binding.tvFindCount.setTextColor(
                MaterialColors.getColor(
                    binding.tvFindCount, com.google.android.material.R.attr.colorError
                )
            )
            return
        }

        val idx = currentIndex()
        binding.tvFindCount.visibility = View.VISIBLE
        binding.tvFindCount.setTextColor(
            MaterialColors.getColor(
                binding.tvFindCount, com.google.android.material.R.attr.colorOnSurfaceVariant
            )
        )
        binding.tvFindCount.text = if (idx >= 0) {
            activity.getString(R.string.find_count, idx + 1, total)
        } else {
            activity.getString(R.string.find_count_total, total)
        }
    }

    // ---------------- 导航 ----------------

    /**
     * 上一个 / 下一个。
     *
     * 按「当前匹配的下标」前后移动，而不是按光标位置比较结束偏移——
     * 后者在选中当前匹配时条件仍然成立，会导致原地不动。
     */
    private fun jump(forward: Boolean) {
        if (matches.isEmpty()) {
            refreshMatches()
            if (matches.isEmpty()) return
        }

        val editor = getEditor()
        val cursor = editor.selectionStart
        val current = matches.indexOfFirst { cursor in it.first..(it.last + 1) }
        val size = matches.size

        val targetIndex = when {
            // 勾了「从头」且当前不在任何匹配里：直接从头/尾开始
            current < 0 && options().fromStart -> if (forward) 0 else size - 1

            forward -> if (current >= 0) {
                (current + 1) % size
            } else {
                matches.indexOfFirst { it.first >= cursor }.takeIf { it >= 0 } ?: 0
            }

            else -> if (current >= 0) {
                (current - 1 + size) % size
            } else {
                matches.indexOfLast { it.last + 1 <= cursor }.takeIf { it >= 0 } ?: (size - 1)
            }
        }

        selectRange(matches[targetIndex])
        updateCount()
    }

    /** 编辑器里光标移动后，让计数跟着刷新 */
    fun onEditorSelectionChanged() {
        if (!isOpen || matches.isEmpty()) return
        updateCount()
    }

    /** 编辑区内容变化（输入、撤销、替换）后重新统计 */
    fun onEditorTextChanged() {
        if (!isOpen) return
        scheduleRefresh()
    }

    private fun selectRange(range: IntRange) {
        val editor = getEditor()
        editor.requestFocus()
        val end = (range.last + 1).coerceAtMost(editor.text?.length ?: 0)
        editor.setSelection(range.first.coerceAtMost(end), end)
        editor.forceBringCursorIntoView()
    }

    // ---------------- 替换 ----------------

    private fun replaceCurrent() {
        if (isReadOnly()) {
            showTip(activity.getString(R.string.read_only_cannot_edit))
            return
        }
        val query = binding.etFindInput.text?.toString().orEmpty()
        if (query.isEmpty()) {
            showTip(activity.getString(R.string.search_empty_query))
            return
        }
        val replacement = binding.etReplaceInput.text?.toString().orEmpty()
        val fb = EditorSearchHelper.replaceOne(getEditor(), query, replacement, options(), false)

        val msg = when (fb) {
            is EditorSearchHelper.ReplaceFeedback.Replaced ->
                activity.getString(R.string.replace_status_replaced_current)
            is EditorSearchHelper.ReplaceFeedback.Located ->
                activity.getString(R.string.replace_status_located)
            is EditorSearchHelper.ReplaceFeedback.NotFound ->
                activity.getString(R.string.search_status_not_found)
            is EditorSearchHelper.ReplaceFeedback.Invalid ->
                activity.getString(
                    R.string.search_status_invalid_regex,
                    FriendlyError.regexMessage(activity, fb.message)
                )
        }
        showTip(msg)
        refreshMatches()
    }

    private fun replaceAll() {
        if (isReadOnly()) {
            showTip(activity.getString(R.string.read_only_cannot_edit))
            return
        }
        val query = binding.etFindInput.text?.toString().orEmpty()
        if (query.isEmpty()) {
            showTip(activity.getString(R.string.search_empty_query))
            return
        }
        val replacement = binding.etReplaceInput.text?.toString().orEmpty()
        val fb = EditorSearchHelper.replaceAll(getEditor(), query, replacement, options())

        val msg = when (fb) {
            is EditorSearchHelper.ReplaceFeedback.Replaced ->
                activity.getString(R.string.replace_status_replaced, fb.count)
            is EditorSearchHelper.ReplaceFeedback.Invalid ->
                activity.getString(
                    R.string.search_status_invalid_regex,
                    FriendlyError.regexMessage(activity, fb.message)
                )
            else -> activity.getString(R.string.search_status_not_found)
        }
        showTip(msg)
        refreshMatches()
    }

    private companion object {
        const val REFRESH_DELAY_MS = 120L
    }
}
