package com.sunwenqing.plainpad

import android.content.ContentResolver
import android.net.Uri
import java.security.MessageDigest

object FileHashHelper {

    private val HEX = "0123456789abcdef".toCharArray()

    /**
     * 计算字符串（UTF-8 字节）的 SHA-256。
     */
    fun sha256OfText(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return toHex(digest.digest(text.toByteArray(Charsets.UTF_8)))
    }

    /**
     * 流式计算文件内容的 SHA-256。
     * 不把整个文件读入内存，适合大文件；
     * 不受编辑器 20MB 截断限制，能算出完整文件的哈希。
     *
     * @return 十六进制哈希；读取失败返回 null。
     */
    fun sha256OfFile(cr: ContentResolver, uri: Uri): String? {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val input = cr.openInputStream(uri) ?: return null
            input.use { stream ->
                val buffer = ByteArray(64 * 1024)
                var n: Int
                while (stream.read(buffer).also { n = it } != -1) {
                    if (n > 0) digest.update(buffer, 0, n)
                }
            }
            toHex(digest.digest())
        } catch (_: Exception) {
            null
        }
    }

    private fun toHex(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            sb.append(HEX[v ushr 4])
            sb.append(HEX[v and 0x0F])
        }
        return sb.toString()
    }
}