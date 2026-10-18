package com.sunwenqing.plainpad

import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

object TextCodec {

    /** 支持保存的编码列表 */
    val SUPPORTED = listOf("UTF-8", "UTF-16LE", "UTF-16BE", "GBK", "GB2312")

    private val GBK: Charset = Charset.forName("GBK")

    data class Result(val text: String, val encoding: String)

    /** 探测编码并解码。返回解码后的文本 + 探测到的编码名 */
    fun detectAndDecode(bytes: ByteArray): Result {
        if (bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() &&
            bytes[1] == 0xBB.toByte() &&
            bytes[2] == 0xBF.toByte()
        ) return Result(String(bytes, 3, bytes.size - 3, Charsets.UTF_8), "UTF-8")

        if (bytes.size >= 2) {
            if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte())
                return Result(String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE), "UTF-16LE")
            if (bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte())
                return Result(String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE), "UTF-16BE")
        }

        return try {
            val text = Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
            Result(text, "UTF-8")
        } catch (e: Exception) {
            Result(String(bytes, GBK), "GBK")
        }
    }

    /** 按指定编码把文本编码为字节数组 */
    fun encode(text: String, encoding: String): ByteArray = when (encoding) {
        "UTF-16LE" -> text.toByteArray(Charsets.UTF_16LE)
        "UTF-16BE" -> text.toByteArray(Charsets.UTF_16BE)
        "GBK" -> text.toByteArray(GBK)
        "GB2312" -> text.toByteArray(Charset.forName("GB2312"))
        else -> text.toByteArray(Charsets.UTF_8)
    }
}