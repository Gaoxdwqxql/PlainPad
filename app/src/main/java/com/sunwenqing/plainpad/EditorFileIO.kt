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

    /** 探测大文件编码时最多读这么多字节 */
    private const val ENCODING_SAMPLE_BYTES = 64 * 1024

    data class LoadResult(
        val text: String,
        val encoding: String,
        val isTruncated: Boolean,
        /** 原文件是否带 BOM（UTF-16 的 BOM 视为编码的一部分） */
        val hasBom: Boolean
    )

    /** 自动探测编码后读取 */
    fun load(cr: ContentResolver, uri: Uri): LoadResult {
        val (bytes, truncated) = readCapped(cr, uri)
        val r = TextCodec.detectAndDecode(bytes)
        return LoadResult(r.text, r.encoding, truncated, r.hasBom)
    }

    /** 用指定编码读取，用于「以指定编码重新打开」 */
    fun loadWithEncoding(cr: ContentResolver, uri: Uri, encoding: String): LoadResult {
        val (bytes, truncated) = readCapped(cr, uri)
        val r = TextCodec.decode(bytes, encoding)
        return LoadResult(r.text, r.encoding, truncated, r.hasBom)
    }

    fun save(cr: ContentResolver, uri: Uri, text: String, encoding: String, withBom: Boolean = false) {
        val out = openOutput(cr, uri) ?: throw IOException("无法写入该文件")
        out.use {
            it.write(TextCodec.encode(text, encoding, withBom))
            it.flush()
        }
    }

    /** 读取原始字节，超过 20MB 截断并标记 */
    private fun readCapped(cr: ContentResolver, uri: Uri): Pair<ByteArray, Boolean> {
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
            return buffer.toByteArray() to isTruncated
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

    /**
     * 以「按需读取」方式打开：不把文件读进内存，只建一份稀疏行索引。
     * 编码不支持或拿不到可 seek 的句柄时返回 null，调用方退回整读路径。
     */
    fun openChunkedSource(
        cr: ContentResolver,
        uri: Uri,
        encoding: String = "UTF-8"
    ): ChunkedTextSource? {
        return try {
            if (uri.scheme == "file") {
                val path = uri.path ?: return null
                ChunkedTextSource.open(java.io.File(path), encoding)
            } else {
                val pfd = cr.openFileDescriptor(uri, "r") ?: return null
                // AutoCloseInputStream 关闭时会一并释放 ParcelFileDescriptor；
                // 数据源 close() 时再回调关闭它，避免 SAF 句柄泄漏
                val stream = android.os.ParcelFileDescriptor.AutoCloseInputStream(pfd)
                try {
                    ChunkedTextSource.open(
                        channel = stream.channel,
                        onClose = { stream.close() },
                        encoding = encoding
                    )
                } catch (e: Exception) {
                    runCatching { stream.close() }
                    throw e
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 只读文件开头一小段来探测编码，供大文件按需读取选 Charset。
     * 读取失败时按 UTF-8 处理，和旧行为一致。
     */
    fun detectEncoding(cr: ContentResolver, uri: Uri): String {
        val sample = readSample(cr, uri) ?: return "UTF-8"
        return TextCodec.detectEncoding(sample)
    }

    private fun readSample(cr: ContentResolver, uri: Uri): ByteArray? {
        return try {
            cr.openInputStream(uri)?.use { input ->
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                var total = 0
                while (total < ENCODING_SAMPLE_BYTES) {
                    val want = minOf(buffer.size, ENCODING_SAMPLE_BYTES - total)
                    val n = input.read(buffer, 0, want)
                    if (n <= 0) break
                    out.write(buffer, 0, n)
                    total += n
                }
                out.toByteArray()
            }
        } catch (_: Exception) {
            null
        }
    }

    /** 只读文件头，判断是否为 PlainPad 加密文本 */
    fun isEncrypted(cr: ContentResolver, uri: Uri): Boolean {
        return try {
            cr.openInputStream(uri)?.use { input ->
                val header = ByteArray(8)
                var read = 0
                while (read < header.size) {
                    val n = input.read(header, read, header.size - read)
                    if (n <= 0) break
                    read += n
                }
                CryptoText.isEncrypted(header.copyOf(read))
            } ?: false
        } catch (_: Exception) {
            false
        }
    }

    /** 读取并解密；超过 20MB 的加密文件不支持 */
    fun loadEncrypted(
        cr: ContentResolver,
        uri: Uri,
        password: CharArray
    ): LoadResult {
        val (bytes, truncated) = readCapped(cr, uri)
        if (truncated) throw IOException("encrypted file is too large")
        val payload = CryptoText.decrypt(bytes, password)
        return LoadResult(payload.text, payload.encoding, isTruncated = false, hasBom = payload.hasBom)
    }

    /** 保存为 PlainPad 加密文本 */
    fun saveEncrypted(
        cr: ContentResolver,
        uri: Uri,
        text: String,
        encoding: String,
        withBom: Boolean,
        password: CharArray
    ) {
        val out = openOutput(cr, uri) ?: throw IOException("无法写入该文件")
        out.use {
            it.write(CryptoText.encrypt(text, encoding, withBom, password))
            it.flush()
        }
    }

    /** 文件字节大小，取不到返回 -1 */
    fun querySize(cr: ContentResolver, uri: Uri): Long {
        return try {
            cr.openFileDescriptor(uri, "r")?.use { it.statSize } ?: -1L
        } catch (_: Exception) {
            -1L
        }
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
