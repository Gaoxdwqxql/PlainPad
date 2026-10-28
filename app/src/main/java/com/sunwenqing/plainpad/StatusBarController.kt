package com.sunwenqing.plainpad

import android.app.Activity
import android.os.Handler
import android.os.Looper
import com.sunwenqing.plainpad.databinding.ActivityMainBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 底部状态栏控制器。
 * 负责：状态文字、编码标签、EOL 标签、行号刷新、自动保存时间、特殊字符渲染。
 */
class StatusBarController(
    private val activity: Activity,
    private val binding: ActivityMainBinding,
    private val prefs: Prefs,
    private val getEditor: () -> PlainEditText,
    private val getCurrentDoc: () -> OpenDocument?,
    private val isLargeFileMode: () -> Boolean
) {

    private val statusHandler = Handler(Looper.getMainLooper())
    private var statusRunnable: Runnable? = null
    private val STATUS_UPDATE_DELAY_MS = 400L

    private val specialCharHandler = Handler(Looper.getMainLooper())
    private var specialCharRunnable: Runnable? = null
    private val SPECIAL_CHAR_REFRESH_DELAY_MS = 150L

    // ---------------- 状态栏更新 ----------------

    fun triggerStatusUpdate() {
        cancelStatusUpdate()
        val r = Runnable {
            updateEolLabel()
            if (isLargeFileMode()) return@Runnable
            updateStatus()
            updateLineNumbers()
        }
        statusRunnable = r
        statusHandler.postDelayed(r, STATUS_UPDATE_DELAY_MS)
    }

    fun cancelStatusUpdate() {
        statusRunnable?.let { statusHandler.removeCallbacks(it) }
        statusRunnable = null
    }

    fun updateStatus() {
        if (isLargeFileMode()) {
            val readOnlyText =
                if (getCurrentDoc()?.isReadOnly == true) "(${activity.getString(R.string.read_only)})" else ""
            val length = getCurrentDoc()?.text?.length ?: 0
            binding.tvStatus.text =
                "${activity.getString(R.string.large_file_mode)}$readOnlyText · ${activity.getString(R.string.chars_suffix, length)}"
            return
        }
        val text = getEditor().text ?: return
        val stats = EditorCommands.computeStats(text)
        val items = prefs.statusBarItems.split(",").map { it.trim() }.filter { it.isNotEmpty() }.take(2)
        val parts = ArrayList<String>(items.size)
        for (id in items) {
            val str = when (id) {
                "chars_space" -> activity.getString(R.string.status_chars_space, stats.charsWithSpace)
                "chars_nospace" -> activity.getString(R.string.status_chars_nospace, stats.charsNoSpace)
                "words" -> activity.getString(R.string.status_words, stats.words)
                "lines" -> activity.getString(R.string.status_lines, stats.totalLines)
                "nonempty_lines" -> activity.getString(R.string.status_nonempty_lines, stats.nonEmptyLines)
                "max_line" -> activity.getString(R.string.status_max_line, stats.maxLineWidth)
                "letters" -> activity.getString(R.string.status_letters, stats.letters)
                "invisible" -> activity.getString(R.string.status_invisible, stats.invisibleChars)
                else -> null
            } ?: continue
            parts.add(str)
        }
        binding.tvStatus.text = parts.joinToString(" · ")
    }

    fun updateEncodingLabel() {
        binding.tvEncoding.text = getCurrentDoc()?.encoding ?: "UTF-8"
    }

    fun updateLineNumbers() {
        if (isLargeFileMode()) return
        binding.tvLineNumbers.invalidate()
    }

    fun updateEolLabel(): String {
        val text = if (isLargeFileMode()) getCurrentDoc()?.text else getEditor().text
        return EolConverter.applyLabel(binding.tvEol, text)
    }

    fun updateAutoSaveStatus() {
        val t = prefs.lastSaveTime
        if (t > 0) {
            val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
            binding.tvAutoSave.text =
                activity.getString(R.string.auto_save_last_time, fmt.format(Date(t)))
        } else {
            binding.tvAutoSave.text = activity.getString(R.string.auto_save_never)
        }
        binding.tvAutoSave.visibility = android.view.View.VISIBLE
    }

    // ---------------- 特殊字符渲染 ----------------

    fun scheduleSpecialCharRefresh() {
        if (isLargeFileMode()) return
        cancelSpecialCharRefresh()
        val r = Runnable {
            SpecialCharRenderer.apply(getEditor(), prefs.showSpecialChars)
        }
        specialCharRunnable = r
        specialCharHandler.postDelayed(r, SPECIAL_CHAR_REFRESH_DELAY_MS)
    }

    fun cancelSpecialCharRefresh() {
        specialCharRunnable?.let { specialCharHandler.removeCallbacks(it) }
        specialCharRunnable = null
    }

    fun cancelAll() {
        cancelStatusUpdate()
        cancelSpecialCharRefresh()
    }
}