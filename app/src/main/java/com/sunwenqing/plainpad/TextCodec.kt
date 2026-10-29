package com.sunwenqing.plainpad

import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

object TextCodec {

    /** 支持保存的编码列表 */
    val SUPPORTED = listOf("UTF-8", "UTF-16LE", "UTF-16BE", "GBK", "GB2312")

    private val GBK: Charset = Charset.forName("GBK")
    private val GB2312: Charset = Charset.forName("GB2312")

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
            val text = Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
            Result(text, "UTF-8", false)
        } catch (e: Exception) {
            Result(String(bytes, GBK), "GBK", false)
        }
    }

    /**
     * 按指定编码把文本编码为字节数组。
     *
     * - UTF-16LE / UTF-16BE：**始终**写入 BOM。UTF-16 没有 BOM 就无法在下次打开时被识别，
     *   会被当成 UTF-8 解码失败后退化为 GBK，内容全乱。
     * - UTF-8：按 [withBom] 决定是否写入 BOM。
     * - GBK / GB2312：没有 BOM 的概念，忽略该参数。
     */
    fun encode(text: String, encoding: String, withBom: Boolean = false): ByteArray = when (encoding) {
        "UTF-16LE" -> BOM_UTF16_LE + text.toByteArray(Charsets.UTF_16LE)
        "UTF-16BE" -> BOM_UTF16_BE + text.toByteArray(Charsets.UTF_16BE)
        "GBK" -> text.toByteArray(GBK)
        "GB2312" -> text.toByteArray(GB2312)
        else -> {
            val body = text.toByteArray(Charsets.UTF_8)
            if (withBom) BOM_UTF8 + body else body
        }
    }
}
