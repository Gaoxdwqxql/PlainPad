package com.sunwenqing.plainpad

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import java.io.File
import java.io.IOException

data class CustomFont(
    val displayName: String,
    val fileName: String = "",
    val uri: String = ""
) {
    val isUri: Boolean get() = uri.isNotEmpty()

    fun matches(other: CustomFont): Boolean =
        if (isUri && other.isUri) uri == other.uri
        else if (!isUri && !other.isUri) fileName == other.fileName
        else false
}

object FontManager {

    const val CUSTOM_PREFIX = "custom:"
    private const val URI_PREFIX = "uri:"

    fun fontsDir(context: Context): File =
        File(context.filesDir, "fonts").apply { mkdirs() }

    fun list(prefs: Prefs): List<CustomFont> = prefs.customFonts

    fun key(font: CustomFont): String =
        if (font.isUri) "$CUSTOM_PREFIX$URI_PREFIX${font.uri}"
        else "$CUSTOM_PREFIX${font.fileName}"

    /** 判断 key 是复制模式的文件名，还是引用模式的 uri */
    fun isUriKey(key: String): Boolean {
        if (!key.startsWith(CUSTOM_PREFIX)) return false
        return key.removePrefix(CUSTOM_PREFIX).startsWith(URI_PREFIX)
    }

    fun uriOfKey(key: String): Uri? {
        if (!isUriKey(key)) return null
        val rest = key.removePrefix(CUSTOM_PREFIX).removePrefix(URI_PREFIX)
        return Uri.parse(rest)
    }

    fun fileNameOfKey(key: String): String? {
        if (!key.startsWith(CUSTOM_PREFIX) || isUriKey(key)) return null
        return key.removePrefix(CUSTOM_PREFIX)
    }

    /**
     * 导入字体。
     * - 复制模式：复制到 filesDir/fonts/，返回 fileName 型 CustomFont
     * - 引用模式：持久化 URI 权限，直接返回 uri 型 CustomFont
     * - API < 26 时强制复制，因为 Typeface.Builder(FileDescriptor) 不可用
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

            val canUseUriMode = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
            val useUriMode = !prefs.copyFontToPrivate && canUseUriMode

            if (useUriMode) {
                try {
                    cr.takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Exception) {}

                val tf = loadTypefaceFromUri(context, uri)
                    ?: return Result.failure(
                        Exception(context.getString(R.string.font_import_invalid))
                    )

                val font = CustomFont(base, "", uri.toString())
                prefs.customFonts = prefs.customFonts + font
                Result.success(font)
            } else {
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

                try {
                    Typeface.createFromFile(dest)
                } catch (e: Exception) {
                    dest.delete()
                    return Result.failure(
                        Exception(context.getString(R.string.font_import_invalid), e)
                    )
                }

                val font = CustomFont(base, fileName, "")
                prefs.customFonts = prefs.customFonts + font
                Result.success(font)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun delete(context: Context, prefs: Prefs, fonts: List<CustomFont>): Int {
        if (fonts.isEmpty()) return 0

        val currentKey = prefs.editorFontFamily
        var currentDeleted = false
        val remaining = prefs.customFonts.toMutableList()

        for (f in fonts) {
            if (f.isUri) {
                try {
                    context.contentResolver.releasePersistableUriPermission(
                        Uri.parse(f.uri), Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Exception) {}
            } else {
                val file = prefs.fontFile(f.fileName)
                if (file.exists()) file.delete()
            }

            remaining.removeAll { it.matches(f) }

            if (currentKey == key(f)) currentDeleted = true
        }

        prefs.customFonts = remaining
        if (currentDeleted) {
            prefs.editorFontFamily = "monospace"
        }
        return fonts.size
    }

    fun migrateLegacy(context: Context, prefs: Prefs) {
        val oldPath = prefs.customFontPath
        if (oldPath.isBlank()) return

        val oldFile = File(oldPath)
        if (!oldFile.exists()) {
            prefs.customFontPath = ""
            if (prefs.editorFontFamily == "custom") {
                prefs.editorFontFamily = "monospace"
            }
            return
        }

        val dir = fontsDir(context)
        val destName = oldFile.name.ifBlank { "custom_font" }
        val dest = File(dir, destName)

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
        val font = list.firstOrNull { it.fileName == destName && !it.isUri }
            ?: CustomFont(
                oldFile.nameWithoutExtension.ifBlank { "custom_font" },
                destName,
                ""
            ).also { list.add(it) }

        prefs.customFonts = list

        if (prefs.editorFontFamily == "custom") {
            prefs.editorFontFamily = key(font)
        }

        prefs.customFontPath = ""

        if (!sameFile && oldFile.parentFile?.absolutePath != dir.absolutePath) {
            try { oldFile.delete() } catch (_: Exception) {}
        }
    }

    /** 通过 URI 加载 Typeface。API 26+ 直接用 fd；低版本返回 null，强制走复制模式。 */
    fun loadTypefaceFromUri(context: Context, uri: Uri): Typeface? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
        return try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                Typeface.Builder(pfd.fileDescriptor).build()
            }
        } catch (_: Exception) {
            null
        }
    }

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