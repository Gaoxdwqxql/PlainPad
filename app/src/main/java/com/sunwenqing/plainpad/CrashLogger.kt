package com.sunwenqing.plainpad

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 崩溃日志。
 *
 * 每次未捕获异常写一份到 filesDir/crashes/crash-<时间>.log，
 * 只保留最近 [MAX_FILES] 份，下次启动由 MainActivity 提示用户查看/复制/删除。
 */
object CrashLogger {

    private const val MAX_FILES = 20

    fun dir(context: Context): File = File(context.filesDir, "crashes").apply { mkdirs() }

    /** 记录一次崩溃。整个过程不能再抛异常，失败就返回 null */
    fun record(context: Context, thread: Thread, throwable: Throwable): File? = try {
        val dir = dir(context)
        val now = Date()
        val name = "crash-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(now) + ".log"

        val trace = StringWriter()
        PrintWriter(trace).use { throwable.printStackTrace(it) }

        val text = StringBuilder()
            .append("Time: ").append(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(now)).append('\n')
            .append("Version: ").append(versionName(context)).append('\n')
            .append("Device: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n')
            .append("Android: ").append(Build.VERSION.RELEASE)
            .append(" (API ").append(Build.VERSION.SDK_INT).append(")\n")
            .append("Thread: ").append(thread.name).append('\n')
            .append("Exception: ").append(throwable.javaClass.name)
            .append(": ").append(throwable.message ?: "").append("\n\n")
            .append(trace.toString())
            .toString()

        val file = File(dir, name)
        file.writeText(text, Charsets.UTF_8)
        prune(dir)
        file
    } catch (_: Throwable) {
        null
    }

    /** 按时间倒序返回所有崩溃日志 */
    fun list(context: Context): List<File> =
        dir(context).listFiles { f -> f.isFile && f.name.endsWith(".log") }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()

    /** 拼接全部日志，供一键复制 */
    fun readAll(context: Context): String =
        list(context).joinToString("\n\n") { file ->
            "===== ${file.name} =====\n" + runCatching {
                file.readText(Charsets.UTF_8)
            }.getOrDefault("")
        }

    fun clear(context: Context) {
        dir(context).listFiles()?.forEach {
            runCatching { it.delete() }
        }
    }

    private fun prune(dir: File) {
        val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".log") }
            ?.sortedByDescending { it.lastModified() }
            ?: return
        files.drop(MAX_FILES).forEach { runCatching { it.delete() } }
    }

    private fun versionName(context: Context): String = try {
        val pm = context.packageManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(context.packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
                .versionName ?: "?"
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(context.packageName, 0).versionName ?: "?"
        }
    } catch (_: Throwable) {
        "?"
    }
}
