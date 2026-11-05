package com.sunwenqing.plainpad

import android.content.Context
import java.io.FileNotFoundException
import java.util.Locale

/**
 * 把异常翻译成用户能看懂的话。
 *
 * SAF / IO 抛出来的信息通常是 "Permission denied"、"ENOSPC" 这类英文或不带上下文的
 * 缩写，直接展示给用户等于没说。这里按异常类型和关键字归类成本地化文案；
 * 实在归不了类的，才把原始信息附在括号里方便排查。
 */
object FriendlyError {

    fun message(context: Context, throwable: Throwable): String {
        val raw = throwable.message.orEmpty().trim()
        val lower = raw.lowercase(Locale.US)
        var known = true

        val resId = when {
            throwable is SecurityException -> R.string.error_permission
            throwable is FileNotFoundException -> R.string.error_not_found
            throwable is OutOfMemoryError -> R.string.error_oom

            lower.contains("permission denied") || lower.contains("eacces") ->
                R.string.error_permission

            lower.contains("no such file") || lower.contains("filenotfound") ||
                lower.contains("enoent") -> R.string.error_not_found

            lower.contains("read-only") || lower.contains("erofs") ->
                R.string.error_read_only_fs

            lower.contains("no space") || lower.contains("enospc") ->
                R.string.error_no_space

            lower.contains("unsupported") -> R.string.error_unsupported

            else -> {
                known = false
                R.string.error_unknown
            }
        }

        val base = context.getString(resId)
        return if (!known && raw.isNotEmpty()) {
            context.getString(R.string.error_with_detail, base, raw)
        } else {
            base
        }
    }

    /** 正则错误：只保留人话，不带 index / pattern 这些技术细节 */
    fun regexMessage(context: Context, rawMessage: String?): String {
        val lower = rawMessage.orEmpty().lowercase(Locale.US)
        val resId = when {
            lower.contains("unclosed group") -> R.string.error_regex_unclosed_group
            lower.contains("unclosed character class") -> R.string.error_regex_unclosed_class
            lower.contains("unmatched closing") -> R.string.error_regex_unmatched_close
            lower.contains("dangling meta") -> R.string.error_regex_dangling_meta
            lower.contains("illegal repetition") -> R.string.error_regex_illegal_repetition
            else -> R.string.error_regex_generic
        }
        return context.getString(resId)
    }
}
