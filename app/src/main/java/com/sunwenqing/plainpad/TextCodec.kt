package com.sunwenqing.plainpad

import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

object TextCodec {

    /**
     * 支持保存 / 重新打开的编码列表。
     * 顺序按使用频率排，弹窗会按这个顺序显示。
     */
    val SUPPORTED = listOf(
        "UTF-8",
        "UTF-16LE",
        "UTF-16BE",
        "GB18030",
        "GBK",
        "GB2312",
        "Big5",
        "Shift_JIS",
        "EUC-KR",
        "Windows-1252",
        "ISO-8859-1"
    )

    private val GBK: Charset = Charset.forName("GBK")
    private val GB2312: Charset = Charset.forName("GB2312")
    private val GB18030: Charset = loadCharset("GB18030", GBK)
    private val BIG5: Charset = loadCharset("Big5", GBK)
    private val SHIFT_JIS: Charset = loadCharset("Shift_JIS", GBK)
    private val EUC_KR: Charset = loadCharset("EUC-KR", GBK)
    private val WINDOWS_1252: Charset = loadCharset("windows-1252", Charsets.ISO_8859_1)

    private val BOM_UTF8 = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
    private val BOM_UTF16_LE = byteArrayOf(0xFF.toByte(), 0xFE.toByte())
    private val BOM_UTF16_BE = byteArrayOf(0xFE.toByte(), 0xFF.toByte())

    /**
     * @param hasBom 原文件是否带 BOM。UTF-16 的 BOM 是识别编码的唯一依据，
     *        UTF-8 的 BOM 则是可选的，由用户设置决定是否写回。
     */
    data class Result(val text: String, val encoding: String, val hasBom: Boolean)

    /** 探测编码并解码。返回解码后的文本 + 探测到的编码名 + 是否带 BOM */
    fun detectAndDecode(bytes: ByteArray): Result {
        if (bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() &&
            bytes[1] == 0xBB.toByte() &&
            bytes[2] == 0xBF.toByte()
        ) return Result(String(bytes, 3, bytes.size - 3, Charsets.UTF_8), "UTF-8", true)

        if (bytes.size >= 2) {
            if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte())
                return Result(String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE), "UTF-16LE", true)
            if (bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte())
                return Result(String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE), "UTF-16BE", true)
        }

        return try {
            val text = decodeStrictUtf8(bytes)
            Result(text, "UTF-8", false)
        } catch (e: Exception) {
            Result(String(bytes, GBK), "GBK", false)
        }
    }

    /**
     * 只探测编码，不返回正文。用于大文件按需读取：先读一小段判断编码，
     * 再决定用哪种编码建立行索引。
     *
     * 样本末尾可能截断一个多字节字符，UTF-8 检测允许丢掉最后 1~3 个字节。
     */
    fun detectEncoding(sample: ByteArray): String {
        if (sample.isEmpty()) return "UTF-8"

        if (sample.size >= 3 &&
            sample[0] == 0xEF.toByte() &&
            sample[1] == 0xBB.toByte() &&
            sample[2] == 0xBF.toByte()
        ) return "UTF-8"

        if (sample.size >= 2) {
            if (sample[0] == 0xFF.toByte() && sample[1] == 0xFE.toByte()) return "UTF-16LE"
            if (sample[0] == 0xFE.toByte() && sample[1] == 0xFF.toByte()) return "UTF-16BE"
        }

        // 完整解码失败时，去掉末尾 1~3 字节再试：只影响样本，不影响正文
        for (trim in 0..3) {
            val end = sample.size - trim
            if (end <= 0) break
            try {
                decodeStrictUtf8(sample, 0, end)
                return "UTF-8"
            } catch (_: Exception) {
                // try next trim
            }
        }

        // 不是合法 UTF-8：保持与旧版一致，先按 GBK 打开；
        // Big5 / Shift_JIS 等可以由用户通过「以指定编码重新打开」切换。
        return "GBK"
    }

    /**
     * 用指定编码解码。用于「以指定编码重新打开」：不再自动探测，
     * 但只要文件开头有与所选编码匹配的 BOM，仍然会剥掉并记录。
     */
    fun decode(bytes: ByteArray, encoding: String): Result {
        val bom = bomOf(encoding)
        val hasBom = bom != null && bytes.size >= bom.size && bom.indices.all { bytes[it] == bom[it] }
        val offset = if (hasBom) bom!!.size else 0
        val text = String(bytes, offset, bytes.size - offset, charsetFor(encoding))
        return Result(text, encoding, hasBom)
    }

    /**
     * 按指定编码把文本编码为字节数组。
     *
     * - UTF-16LE / UTF-16BE：**始终**写入 BOM。UTF-16 没有 BOM 就无法在下次打开时被识别，
     *   会被当成 UTF-8 解码失败后退化为 GBK，内容全乱。
     * - UTF-8：按 [withBom] 决定是否写入 BOM。
     * - 其他编码没有 BOM 的概念，忽略该参数。
     */
    fun encode(text: String, encoding: String, withBom: Boolean = false): ByteArray = when (encoding) {
        "UTF-16LE" -> BOM_UTF16_LE + text.toByteArray(Charsets.UTF_16LE)
        "UTF-16BE" -> BOM_UTF16_BE + text.toByteArray(Charsets.UTF_16BE)
        "GB18030" -> text.toByteArray(GB18030)
        "GBK" -> text.toByteArray(GBK)
        "GB2312" -> text.toByteArray(GB2312)
        "Big5" -> text.toByteArray(BIG5)
        "Shift_JIS" -> text.toByteArray(SHIFT_JIS)
        "EUC-KR" -> text.toByteArray(EUC_KR)
        "Windows-1252" -> text.toByteArray(WINDOWS_1252)
        "ISO-8859-1" -> text.toByteArray(Charsets.ISO_8859_1)
        else -> {
            val body = text.toByteArray(Charsets.UTF_8)
            if (withBom) BOM_UTF8 + body else body
        }
    }

    /** 编码对应的 Charset；未知编码按 UTF-8 处理 */
    fun charsetFor(encoding: String): Charset = when (encoding) {
        "UTF-16LE" -> Charsets.UTF_16LE
        "UTF-16BE" -> Charsets.UTF_16BE
        "GB18030" -> GB18030
        "GBK" -> GBK
        "GB2312" -> GB2312
        "Big5" -> BIG5
        "Shift_JIS" -> SHIFT_JIS
        "EUC-KR" -> EUC_KR
        "Windows-1252" -> WINDOWS_1252
        "ISO-8859-1" -> Charsets.ISO_8859_1
        else -> Charsets.UTF_8
    }

    /**
     * 是否可用于大文件按需读取。
     * UTF-16/UTF-32 的换行字节里含 0x00，不能直接按 0x0A 建行索引；
     * 其余列出编码都是 ASCII 兼容的，0x0A 不会出现在多字节字符内部。
     */
    fun supportsChunked(encoding: String): Boolean =
        !encoding.startsWith("UTF-16", ignoreCase = true) &&
                !encoding.startsWith("UTF-32", ignoreCase = true)

    private fun bomOf(encoding: String): ByteArray? = when (encoding) {
        "UTF-8" -> BOM_UTF8
        "UTF-16LE" -> BOM_UTF16_LE
        "UTF-16BE" -> BOM_UTF16_BE
        else -> null
    }

    private fun decodeStrictUtf8(bytes: ByteArray, start: Int = 0, end: Int = bytes.size): String =
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes, start, end - start))
            .toString()

    private fun loadCharset(name: String, fallback: Charset): Charset =
        runCatching { Charset.forName(name) }.getOrDefault(fallback)
}
