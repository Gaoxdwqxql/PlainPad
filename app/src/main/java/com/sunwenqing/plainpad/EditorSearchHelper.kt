package com.sunwenqing.plainpad

import android.widget.EditText
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

object EditorSearchHelper {

    sealed class FindFeedback {
        object Found : FindFeedback()
        object NotFound : FindFeedback()
        data class Invalid(val message: String) : FindFeedback()
    }

    sealed class ReplaceFeedback {
        data class Replaced(val count: Int) : ReplaceFeedback()
        object Located : ReplaceFeedback()
        object NotFound : ReplaceFeedback()
        data class Invalid(val message: String) : ReplaceFeedback()
    }

    private sealed class MatchResult {
        data class Match(val start: Int, val end: Int) : MatchResult()
        object None : MatchResult()
        data class Invalid(val message: String) : MatchResult()
    }

    private sealed class ReplaceAllOutcome {
        data class Ok(val text: String, val count: Int) : ReplaceAllOutcome()
        object None : ReplaceAllOutcome()
        data class Invalid(val message: String) : ReplaceAllOutcome()
    }

    // ---------------- 查找 ----------------

    /**
     * @param isFirstSearch 是否为本弹窗打开后的首次查找。
     *        当 options.fromStart 为 true 且 isFirstSearch 为 true 时，
     *        忽略当前光标位置，从文本开头找第一个匹配，并强制向下。
     */
    fun findNext(
        editor: EditText,
        query: String,
        forward: Boolean,
        options: SearchOptions,
        isFirstSearch: Boolean = false
    ): FindFeedback {
        if (query.isEmpty()) return FindFeedback.NotFound
        val text = editor.text?.toString().orEmpty()
        if (text.isEmpty()) return FindFeedback.NotFound

        val useStart = isFirstSearch && options.fromStart
        val selStart = if (useStart) 0 else editor.selectionStart
        val selEnd = if (useStart) 0 else editor.selectionEnd
        val effectiveForward = if (useStart) true else forward

        val result = if (options.regex) {
            findRegex(text, query, effectiveForward, options.caseSensitive, selStart, selEnd)
        } else {
            findPlain(text, query, effectiveForward, options.caseSensitive, selStart, selEnd)
        }

        return when (result) {
            is MatchResult.Match -> {
                editor.requestFocus()
                editor.setSelection(result.start, result.end)
                FindFeedback.Found
            }
            is MatchResult.None -> FindFeedback.NotFound
            is MatchResult.Invalid -> FindFeedback.Invalid(result.message)
        }
    }

    private fun findPlain(
        text: String,
        query: String,
        forward: Boolean,
        caseSensitive: Boolean,
        selStart: Int,
        selEnd: Int
    ): MatchResult {
        return if (forward) {
            val from = selEnd.coerceAtLeast(0)
            var index = text.indexOf(query, from, ignoreCase = !caseSensitive)
            if (index < 0) index = text.indexOf(query, 0, ignoreCase = !caseSensitive)
            if (index < 0) MatchResult.None
            else MatchResult.Match(index, index + query.length)
        } else {
            val from = (selStart.coerceAtMost(text.length) - 1).coerceAtLeast(0)
            var index = text.lastIndexOf(query, from, ignoreCase = !caseSensitive)
            if (index < 0) index = text.lastIndexOf(query, text.length, ignoreCase = !caseSensitive)
            if (index < 0) MatchResult.None
            else MatchResult.Match(index, index + query.length)
        }
    }

    private fun findRegex(
        text: String,
        query: String,
        forward: Boolean,
        caseSensitive: Boolean,
        selStart: Int,
        selEnd: Int
    ): MatchResult {
        val pattern = try {
            compileRegex(query, caseSensitive)
        } catch (e: PatternSyntaxException) {
            return MatchResult.Invalid(e.message ?: "Invalid regex")
        }

        return if (forward) {
            val matcher = pattern.matcher(text)
            val from = selEnd.coerceAtLeast(0)
            if (matcher.find(from)) {
                do {
                    if (matcher.end() > matcher.start()) {
                        return MatchResult.Match(matcher.start(), matcher.end())
                    }
                } while (matcher.find())
            }
            matcher.reset()
            do {
                if (matcher.end() > matcher.start()) {
                    return MatchResult.Match(matcher.start(), matcher.end())
                }
            } while (matcher.find())
            MatchResult.None
        } else {
            val matcher = pattern.matcher(text)
            var lastStart = -1
            var lastEnd = -1
            val from = selStart.coerceAtLeast(0)
            while (matcher.find()) {
                if (matcher.start() >= from) break
                if (matcher.end() > matcher.start()) {
                    lastStart = matcher.start()
                    lastEnd = matcher.end()
                }
            }
            if (lastStart >= 0) return MatchResult.Match(lastStart, lastEnd)

            matcher.reset()
            while (matcher.find()) {
                if (matcher.end() > matcher.start()) {
                    lastStart = matcher.start()
                    lastEnd = matcher.end()
                }
            }
            if (lastStart >= 0) MatchResult.Match(lastStart, lastEnd) else MatchResult.None
        }
    }

    // ---------------- 替换 ----------------

    /**
     * @param isFirstSearch 是否为本弹窗打开后的首次替换。
     *        当 options.fromStart 为 true 且 isFirstSearch 为 true 时，
     *        不检查当前选区，直接定位到第一个匹配，由用户再次点击替换。
     */
    fun replaceOne(
        editor: EditText,
        query: String,
        replacement: String,
        options: SearchOptions,
        isFirstSearch: Boolean = false
    ): ReplaceFeedback {
        if (query.isEmpty()) return ReplaceFeedback.NotFound
        val editable = editor.text ?: return ReplaceFeedback.NotFound
        val text = editable.toString()
        if (text.isEmpty()) return ReplaceFeedback.NotFound

        // 首次且勾选"从头开始"：只定位第一个匹配，不替换
        if (isFirstSearch && options.fromStart) {
            val next = findNext(editor, query, true, options, isFirstSearch = true)
            return when (next) {
                is FindFeedback.Found -> ReplaceFeedback.Located
                is FindFeedback.NotFound -> ReplaceFeedback.NotFound
                is FindFeedback.Invalid -> ReplaceFeedback.Invalid(next.message)
            }
        }

        val s = editor.selectionStart
        val e = editor.selectionEnd
        var replaced = false

        if (s in 0 until e) {
            val selected = text.substring(s, e)
            if (options.regex) {
                val pattern = try {
                    compileRegex(query, options.caseSensitive)
                } catch (ex: PatternSyntaxException) {
                    return ReplaceFeedback.Invalid(ex.message ?: "Invalid regex")
                }
                val m = pattern.matcher(selected)
                if (m.matches() && m.end() > m.start()) {
                    val sb = StringBuilder()
                    m.appendReplacement(sb, replacement)
                    m.appendTail(sb)
                    editable.replace(s, e, sb.toString())
                    replaced = true
                }
            } else {
                if (selected.equals(query, ignoreCase = !options.caseSensitive)) {
                    editable.replace(s, e, replacement)
                    replaced = true
                }
            }
        }

        val next = findNext(editor, query, true, options, isFirstSearch = false)
        return when {
            replaced -> ReplaceFeedback.Replaced(1)
            next is FindFeedback.Invalid -> ReplaceFeedback.Invalid(next.message)
            next is FindFeedback.Found -> ReplaceFeedback.Located
            else -> ReplaceFeedback.NotFound
        }
    }

    fun replaceAll(
        editor: EditText,
        query: String,
        replacement: String,
        options: SearchOptions
    ): ReplaceFeedback {
        if (query.isEmpty()) return ReplaceFeedback.NotFound
        val editable = editor.text ?: return ReplaceFeedback.NotFound
        val text = editable.toString()
        if (text.isEmpty()) return ReplaceFeedback.NotFound

        val outcome = if (options.regex) {
            replaceAllRegex(text, query, replacement, options.caseSensitive)
        } else {
            replaceAllPlain(text, query, replacement, options.caseSensitive)
        }

        return when (outcome) {
            is ReplaceAllOutcome.Ok -> {
                if (outcome.count == 0) return ReplaceFeedback.NotFound
                val caret = editor.selectionStart.coerceAtLeast(0)
                editor.setText(outcome.text)
                editor.setSelection(caret.coerceAtMost(editor.text?.length ?: 0))
                ReplaceFeedback.Replaced(outcome.count)
            }
            is ReplaceAllOutcome.None -> ReplaceFeedback.NotFound
            is ReplaceAllOutcome.Invalid -> ReplaceFeedback.Invalid(outcome.message)
        }
    }

    private fun replaceAllPlain(
        text: String,
        query: String,
        replacement: String,
        caseSensitive: Boolean
    ): ReplaceAllOutcome {
        val sb = StringBuilder(text.length)
        var i = 0
        var count = 0
        while (i < text.length) {
            if (i + query.length <= text.length &&
                text.regionMatches(i, query, 0, query.length, ignoreCase = !caseSensitive)
            ) {
                sb.append(replacement)
                i += query.length
                count++
            } else {
                sb.append(text[i])
                i++
            }
        }
        return if (count == 0) ReplaceAllOutcome.None
        else ReplaceAllOutcome.Ok(sb.toString(), count)
    }

    private fun replaceAllRegex(
        text: String,
        query: String,
        replacement: String,
        caseSensitive: Boolean
    ): ReplaceAllOutcome {
        val pattern = try {
            compileRegex(query, caseSensitive)
        } catch (e: PatternSyntaxException) {
            return ReplaceAllOutcome.Invalid(e.message ?: "Invalid regex")
        }

        val matcher = pattern.matcher(text)
        val sb = StringBuilder(text.length)
        var count = 0
        while (matcher.find()) {
            if (matcher.end() == matcher.start()) continue
            matcher.appendReplacement(sb, replacement)
            count++
        }
        matcher.appendTail(sb)

        return if (count == 0) ReplaceAllOutcome.None
        else ReplaceAllOutcome.Ok(sb.toString(), count)
    }

    // ---------------- 工具 ----------------

    private fun compileRegex(query: String, caseSensitive: Boolean): Pattern {
        val flags = if (caseSensitive) 0
        else Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE
        return Pattern.compile(query, flags)
    }
}