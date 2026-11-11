package com.sunwenqing.plainpad

import android.util.Log
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale

/**
 * 渲染相关的运行时诊断数据，只被开发者选项读取。
 * 用静态字段是为了让开发者选项能直接看到当前编辑器状态，避免层层传参。
 */
object DevDiag {

    private const val TAG = "PlainPadRender"
    private const val MAX_LOG_LINES = 200

    /** 强制按需读取的阈值，必须和 MainActivity 的常量保持一致 */
    private const val FORCED_THRESHOLD_BYTES = 1L * 1024 * 1024
    private const val DEFAULT_THRESHOLD_BYTES = 12L * 1024 * 1024

    /** 详细日志开关（打开后关键路径输出 Log.d，并记录到诊断弹窗） */
    var verbose: Boolean = false
        set(value) {
            field = value
            event("verbose logging " + if (value) "enabled" else "disabled")
            if (value) Log.d(TAG, "verbose logging enabled")
        }

    /** 强制把按需读取阈值降到 1MB，便于用小文件验证 */
    var forceChunkedThreshold: Boolean = false

    // ---------------- 当前文档 ----------------

    var docName: String = ""
    var docBytes: Long = 0L
    var docChars: Int = 0
    var docLines: Int = 0
    var docChunked: Boolean = false
    var docIndexEntries: Int = 0
    var docIndexMs: Long = 0L

    /** 当前实际生效的按需读取阈值 */
    val thresholdBytes: Long
        get() = if (forceChunkedThreshold) FORCED_THRESHOLD_BYTES else DEFAULT_THRESHOLD_BYTES

    // ---------------- 上一帧 ----------------

    var lastLineCount: Int = 0
    var lastChunked: Boolean = false
    var lastFrameBytes: Int = 0
    var lastFrameMs: Long = 0L
    var lastFrameDrawMs: Long = 0L
    var lastFrameCached: Boolean = false
    var lastFrameFrom: Int = 0
    var lastFrameTo: Int = 0

    private val logLines = ArrayDeque<String>(MAX_LOG_LINES)
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    /** 切文档时刷新当前文档信息，同时留一条事件日志 */
    fun resetDocument(
        name: String,
        bytes: Long,
        chars: Int,
        lines: Int,
        chunked: Boolean,
        indexEntries: Int = 0,
        indexMs: Long = 0L
    ) {
        docName = name
        docBytes = bytes
        docChars = chars
        docLines = lines
        docChunked = chunked
        docIndexEntries = indexEntries
        docIndexMs = indexMs
        lastFrameBytes = 0
        lastFrameMs = 0L
        lastFrameDrawMs = 0L
        lastFrameCached = false
        lastFrameFrom = 0
        lastFrameTo = 0

        event(
            "open \"$name\" bytes=$bytes chars=$chars lines=$lines " +
                    "source=${if (chunked) "chunked" else "memory"} " +
                    "index=$indexEntries indexMs=$indexMs"
        )
    }

    /**
     * 每绘制一帧调用一次；字段始终更新，日志只在详细模式下写，避免正常使用拖慢绘制。
     * [bytes]/[ms] 是当前可视窗口的读取量与读取耗时；窗口被复用时 [cached] 为 true。
     * [frameMs] 是包含文字绘制的整帧耗时。
     */
    fun recordFrame(
        totalLines: Int,
        chunked: Boolean,
        from: Int,
        to: Int,
        bytes: Int,
        ms: Long,
        cached: Boolean,
        frameMs: Long
    ) {
        lastLineCount = totalLines
        lastChunked = chunked
        lastFrameFrom = from
        lastFrameTo = to
        lastFrameBytes = bytes
        lastFrameMs = ms
        lastFrameCached = cached
        lastFrameDrawMs = frameMs
        if (verbose) {
            detail(
                "frame lines=$from..$to bytes=$bytes readMs=$ms drawMs=$frameMs " +
                        "cached=$cached total=$totalLines chunked=$chunked"
            )
        }
    }

    /** 普通事件：始终留在弹窗日志里，详细模式下同时写 logcat */
    fun event(message: String) {
        append(message)
        if (verbose) Log.d(TAG, message)
    }

    /** 细节日志：只有详细模式才记录，用于每一帧的读写 */
    fun detail(message: String) {
        if (!verbose) return
        append(message)
        Log.d(TAG, message)
    }

    private fun append(message: String) {
        // SimpleDateFormat 不是线程安全的；索引构建在 IO 线程，绘制在主线程
        val stamp = synchronized(timeFormat) { timeFormat.format(Date()) }
        val line = stamp + "  " + message
        synchronized(logLines) {
            logLines.addLast(line)
            while (logLines.size > MAX_LOG_LINES) logLines.removeFirst()
        }
    }

    fun recentLogs(): List<String> = synchronized(logLines) { logLines.toList() }

    fun clearLogs() {
        synchronized(logLines) { logLines.clear() }
    }

    fun describe(): String = buildString {
        appendLine("Document: " + docName.ifEmpty { "-" })
        if (docChunked) {
            appendLine("Source: on-demand read (chunked)")
            appendLine("File size: " + formatBytes(docBytes) + " ($docBytes bytes)")
            appendLine("Line index: $docIndexEntries entries (built in $docIndexMs ms)")
        } else {
            appendLine("Source: in-memory")
            appendLine("Text length: " + formatNumber(docChars.toLong()) + " chars")
        }
        appendLine("Lines: " + formatNumber(docLines.toLong()))
        appendLine(
            "Chunk threshold: " + formatBytes(thresholdBytes) +
                    (if (forceChunkedThreshold) " (forced 1MB)" else "")
        )
        appendLine(
            "Last frame: lines ${lastFrameFrom}..${lastFrameTo} of " +
                    formatNumber(lastLineCount.toLong()) +
                    ", ${formatBytes(lastFrameBytes.toLong())} read in ${lastFrameMs} ms" +
                    (if (lastFrameCached) " (window reused)" else "") +
                    ", draw ${lastFrameDrawMs} ms"
        )
        appendLine("Verbose log: " + (if (verbose) "on" else "off"))
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format(Locale.US, "%.2f MB", mb)
        return String.format(Locale.US, "%.2f GB", mb / 1024.0)
    }

    private fun formatNumber(value: Long): String =
        String.format(Locale.US, "%,d", value)
}
