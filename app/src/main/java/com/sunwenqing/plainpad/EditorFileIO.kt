package com.sunwenqing.plainpad

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import java.io.ByteArrayOutputStream
import java.io.IOException

object EditorFileIO {

    // ★ 放开到 20MB，应对绝大多数日常大文本文件
    private const val MAX_READ_BYTES = 20 * 1024 * 1024

    data class LoadResult(
        val text: String,
        val encoding: String,
        val isTruncated: Boolean
    )

    fun load(cr: ContentResolver, uri: Uri): LoadResult {
        val inputStream = cr.openInputStream(uri) ?: throw IOException("无法读取该文件")
        inputStream.use { stream ->
            val buffer = ByteArrayOutputStream()
            val data = ByteArray(8192)
            var totalRead = 0
            var isTruncated = false
            var nRead: Int

            while (stream.read(data).also { nRead = it } != -1) {
                if (totalRead + nRead > MAX_READ_BYTES) {
                    val remain = MAX_READ_BYTES - totalRead
                    if (remain > 0) buffer.write(data, 0, remain)
                    isTruncated = true
                    break
                }
                buffer.write(data, 0, nRead)
                totalRead += nRead
            }

            val r = TextCodec.detectAndDecode(buffer.toByteArray())
            return LoadResult(r.text, r.encoding, isTruncated)
        }
    }

    fun save(cr: ContentResolver, uri: Uri, text: String, encoding: String) {
        val out = openOutput(cr, uri) ?: throw IOException("无法写入该文件")
        out.use {
            it.write(TextCodec.encode(text, encoding))
            it.flush()
        }
    }

    private fun openOutput(cr: ContentResolver, uri: Uri) = try {
        cr.openOutputStream(uri, "wt")
    } catch (e: Exception) {
        cr.openOutputStream(uri, "w")
    }

    fun queryName(cr: ContentResolver, uri: Uri, fallback: String): String {
        if ("content" == uri.scheme) {
            try {
                cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) {
                        val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (i >= 0) c.getString(i)?.let { return it }
                    }
                }
            } catch (_: Exception) {}
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: fallback
    }

    fun takePersistable(cr: ContentResolver, uri: Uri) {
        if ("content" != uri.scheme) return
        for (flag in intArrayOf(
            Intent.FLAG_GRANT_READ_URI_PERMISSION,
            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )) {
            try { cr.takePersistableUriPermission(uri, flag) } catch (_: Exception) {}
        }
    }
}