package com.sunwenqing.plainpad

import android.os.Handler
import android.os.Looper
import android.widget.EditText

data class Snapshot(val text: String, val selStart: Int, val selEnd: Int)

class UndoManager(
    private val editor: EditText,
    private val maxSize: Int = 100
) {

    private val stack = ArrayDeque<Snapshot>()
    private var index = -1
    private val handler = Handler(Looper.getMainLooper())
    private var pending: Runnable? = null

    // 如果快照内容大于 5MB（约 250 万字符），就不存了，防止内存崩溃
    private val MAX_CHARS_FOR_UNDO = 500_000

    fun reset(text: String) {
        pending?.let { handler.removeCallbacks(it) }
        stack.clear()
        index = 0
        if (text.length <= MAX_CHARS_FOR_UNDO) {
            stack.addLast(Snapshot(text, 0, 0))
        }
    }

    fun scheduleSnapshot() {
        pending?.let { handler.removeCallbacks(it) }
        val r = Runnable { push() }
        pending = r
        handler.postDelayed(r, 500)
    }

    fun undo(): Snapshot? {
        flushPending()
        if (index <= 0 || stack.isEmpty()) return null
        index--
        return stack[index]
    }

    fun redo(): Snapshot? {
        flushPending()
        if (index >= stack.size - 1 || stack.isEmpty()) return null
        index++
        return stack[index]
    }

    fun release() {
        pending?.let { handler.removeCallbacks(it) }
        pending = null
        stack.clear()
    }

    private fun flushPending() {
        pending?.let {
            handler.removeCallbacks(it)
            pending = null
            push()
        }
    }

    private fun push() {
        val editable = editor.text ?: return
        // 超过上限直接清空，放弃存储
        if (editable.length > MAX_CHARS_FOR_UNDO) {
            stack.clear()
            index = -1
            return
        }

        val text = editable.toString()
        val s = editor.selectionStart.coerceAtLeast(0)
        val e = editor.selectionEnd.coerceAtLeast(0)

        if (index in stack.indices && stack[index].text == text) return

        while (stack.size > index + 1) stack.removeLast()
        stack.addLast(Snapshot(text, s, e))
        while (stack.size > maxSize) stack.removeFirst()
        index = stack.size - 1
    }
}