package com.sunwenqing.plainpad

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import java.io.File
import java.io.IOException

/**
 * 自定义字体条目。
 * - displayName：界面上显示的名字
 * - fileName：filesDir/fonts/ 下的文件名
 */
data class CustomFont(
    val displayName: String,
    val fileName: String
)

/**
 * 字体管理核心：
 * - 统一存放目录 filesDir/fonts/
 * - 导入多个字体，自动生成唯一文件名
 * - 单个/批量删除
 * - 旧版单字体 customFontPath 的迁移
 */
object FontManager {

    /** 自定义字体在 editorFontFamily 中的 key 前缀 */
    const val CUSTOM_PREFIX = "custom:"

    /** 字体存放目录：filesDir/fonts/ */
    fun fontsDir(context: Context): File =
        File(context.filesDir, "fonts").apply { mkdirs() }

    /** 当前已导入的自定义字体列表 */
    fun list(prefs: Prefs): List<CustomFont> = prefs.customFonts

    /** 把 CustomFont 转成 editorFontFamily 里存储的 key */
    fun key(font: CustomFont): String = CUSTOM_PREFIX + font.fileName

    /** 把 key 还原成文件名；不是自定义 key 时返回 null */
    fun fileNameOf(key: String): String? =
        if (key.startsWith(CUSTOM_PREFIX)) key.removePrefix(CUSTOM_PREFIX) else null

    /**
     * 导入一个字体文件。
     *
     * 行为：
     * - 从 SAF Uri 复制到 filesDir/fonts/，文件名自动去重
     * - 用 Typeface.createFromFile 校验，失败则删除临时文件并返回失败
     * - 成功后写入 Prefs.customFonts，但不会自动切换当前字体
     */
    fun importFont(context: Context, prefs: Prefs, uri: Uri): Result<CustomFont> {
        return try {
            val cr = context.contentResolver
            val originalName = EditorFileIO.queryName(cr, uri, "font.ttf")

            val base = originalName.substringBeforeLast('.', originalName)
                .ifBlank { "font" }
                .replace(Regex("[^a-zA-Z0-9_\\u4e00-\\u9fff-]"), "_")
                .take(32)
                .ifBlank { "font" }

            val ext = originalName.substringAfterLast('.', "")
                .ifBlank { "ttf" }
                .lowercase()

            val dir = fontsDir(context)
            val fileName = uniqueFileName(dir, base, ext)
            val dest = File(dir, fileName)

            val input = cr.openInputStream(uri)
                ?: return Result.failure(
                    IOException(context.getString(R.string.font_import_read_failed))
                )

            input.use { src ->
                dest.outputStream().use { out -> src.copyTo(out) }
            }

            // 校验字体是否可解析
            try {
                Typeface.createFromFile(dest)
            } catch (e: Exception) {
                dest.delete()
                return Result.failure(
                    Exception(context.getString(R.string.font_import_invalid), e)
                )
            }

            val font = CustomFont(base, fileName)
            val newList = prefs.customFonts.toMutableList().apply { add(font) }
            prefs.customFonts = newList

            Result.success(font)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 删除一组自定义字体。
     *
     * 行为：
     * - 删除字体文件
     * - 从 Prefs.customFonts 移除
     * - 如果当前正在使用被删除的字体，回退到 monospace
     *
     * @return 实际删除的数量
     */
    fun delete(prefs: Prefs, fonts: List<CustomFont>): Int {
        if (fonts.isEmpty()) return 0

        val currentKey = prefs.editorFontFamily
        var currentDeleted = false
        val remaining = prefs.customFonts.toMutableList()

        for (f in fonts) {
            val file = prefs.fontFile(f.fileName)
            if (file.exists()) file.delete()

            remaining.removeAll { it.fileName == f.fileName }

            if (currentKey == key(f)) currentDeleted = true
        }

        prefs.customFonts = remaining
        if (currentDeleted) {
            prefs.editorFontFamily = "monospace"
        }
        return fonts.size
    }

    /**
     * 兼容旧版：旧版只有一个 customFontPath。
     * 迁移到 filesDir/fonts，加入列表，并把 "custom" 改成新 key。
     *
     * 幂等：多次调用不会重复添加。
     */
    fun migrateLegacy(context: Context, prefs: Prefs) {
        val oldPath = prefs.customFontPath
        if (oldPath.isBlank()) return

        val oldFile = File(oldPath)
        if (!oldFile.exists()) {
            prefs.customFontPath = ""
            // 旧文件已经不存在，如果当前仍是 "custom" 就回退
            if (prefs.editorFontFamily == "custom") {
                prefs.editorFontFamily = "monospace"
            }
            return
        }

        val dir = fontsDir(context)
        val destName = oldFile.name.ifBlank { "custom_font" }
        val dest = File(dir, destName)

        // 如果旧文件不在 fonts 目录，复制过去；如果已经在目录里，不复制
        val sameFile = try {
            oldFile.canonicalPath == dest.canonicalPath
        } catch (_: Exception) {
            oldFile.absolutePath == dest.absolutePath
        }

        if (!sameFile && !dest.exists()) {
            try {
                oldFile.copyTo(dest, overwrite = true)
            } catch (_: Exception) {
                return
            }
        }

        val list = prefs.customFonts.toMutableList()
        val font = list.firstOrNull { it.fileName == destName }
            ?: CustomFont(
                oldFile.nameWithoutExtension.ifBlank { "custom_font" },
                destName
            ).also { list.add(it) }

        prefs.customFonts = list

        if (prefs.editorFontFamily == "custom") {
            prefs.editorFontFamily = key(font)
        }

        prefs.customFontPath = ""

        // 旧文件不在 fonts 目录且已复制成功，删除旧文件
        if (!sameFile && oldFile.parentFile?.absolutePath != dir.absolutePath) {
            try { oldFile.delete() } catch (_: Exception) {}
        }
    }

    // ---------------- 内部工具 ----------------

    /** 在目录中生成不冲突的文件名：base.ext / base_1.ext / base_2.ext ... */
    private fun uniqueFileName(dir: File, base: String, ext: String): String {
        var candidate = "$base.$ext"
        var index = 1
        while (File(dir, candidate).exists()) {
            candidate = "${base}_$index.$ext"
            index++
        }
        return candidate
    }
}