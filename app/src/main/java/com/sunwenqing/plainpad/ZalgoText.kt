package com.sunwenqing.plainpad

import android.text.InputFilter
import android.text.Spanned

/**
 * Zalgo 文本（在一个基字符上堆很多组合字符）会让文字排版和滚动明显变慢。
 * 关闭「允许 Zalgo」时：
 * - 大文件渲染会限制每个基字符最多保留 [MAX_MARKS] 个组合字符；
 * - 编辑器会拒绝继续输入/粘贴超过限制的组合字符。
 *
 * 只影响显示和后续输入，不改写已经打开的文件；保存时仍保留原文。
 */
object ZalgoText {

    const val MAX_MARKS = 2

    fun isCombining(codePoint: Int): Boolean = when (Character.getType(codePoint)) {
        Character.NON_SPACING_MARK.toInt(),
        Character.COMBINING_SPACING_MARK.toInt(),
        Character.ENCLOSING_MARK.toInt() -> true
        else -> false
    }

    /** 是否存在超过限制的连续组合字符 */
    fun hasExcess(text: CharSequence): Boolean {
        var run = 0
        var i = 0
        while (i < text.length) {
            val cp = Character.codePointAt(text, i)
            if (isCombining(cp)) {
                run++
                if (run > MAX_MARKS) return true
            } else {
                run = 0
            }
            i += Character.charCount(cp)
        }
        return false
    }

    /**
     * 返回限制后的文本；没有超限时返回原对象，避免无谓分配。
     */
    fun limit(text: CharSequence): CharSequence {
        if (!hasExcess(text)) return text

        val out = StringBuilder(text.length)
        var run = 0
        var i = 0
        while (i < text.length) {
            val cp = Character.codePointAt(text, i)
            val count = Character.charCount(cp)
            if (isCombining(cp)) {
                run++
                if (run <= MAX_MARKS) {
                    out.append(text, i, i + count)
                }
            } else {
                run = 0
                out.append(text, i, i + count)
            }
            i += count
        }
        return out.toString()
    }

    /** 关闭 Zalgo 时给 EditText 用的输入过滤器 */
    fun inputFilter(): InputFilter = object : InputFilter {
        override fun filter(
            source: CharSequence,
            start: Int,
            end: Int,
            dest: Spanned,
            dstart: Int,
            dend: Int
        ): CharSequence? = filterInput(source, start, end, dest, dstart)
    }

    /**
     * 只处理本次插入的内容，同时把光标前已有的连续组合字符算进去，
     * 这样逐字输入大量组合字符也会被拦住。
     */
    fun filterInput(
        source: CharSequence,
        start: Int,
        end: Int,
        dest: Spanned,
        dstart: Int
    ): CharSequence? {
        if (start == end) return null

        var run = 0
        var pos = dstart
        while (pos > 0) {
            val cp = Character.codePointBefore(dest, pos)
            if (!isCombining(cp)) break
            run++
            pos -= Character.charCount(cp)
        }

        var changed = false
        val out = StringBuilder(end - start)
        var i = start
        while (i < end) {
            val cp = Character.codePointAt(source, i)
            val count = Character.charCount(cp)
            if (isCombining(cp)) {
                run++
                if (run <= MAX_MARKS) out.append(source, i, i + count) else changed = true
            } else {
                run = 0
                out.append(source, i, i + count)
            }
            i += count
        }
        return if (changed) out.toString() else null
    }
}
